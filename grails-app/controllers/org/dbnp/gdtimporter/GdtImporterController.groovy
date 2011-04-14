/**
 *  GDTImporter, a plugin for importing data into Grails Domain Templates
 *  Copyright (C) 2011 Tjeerd Abma
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  $Author$
 *  $Rev$
 *  $Date$
 */

package org.dbnp.gdtimporter

import org.dbnp.gdt.*
import grails.converters.JSON
import org.codehaus.groovy.grails.plugins.web.taglib.ValidationTagLib
//import grails.plugins.springsecurity.Secured
import org.codehaus.groovy.grails.commons.ApplicationHolder as AH

//@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class GdtImporterController {
    def authenticationService
	def fileService
	def gdtImporterService
    def gdtService
    def validationTagLib = new ValidationTagLib()

    /**
	 * index method, redirect to the webflow
	 * @void
	 */
	def index = {
		redirect(action: 'pages')
	}

    /**
	 * WebFlow definition
	 * @void
	 */
	def pagesFlow = {
		// start the flow
		onStart {

			// define variables in the flow scope which is availabe
			// throughout the complete webflow also have a look at
			// the Flow Scopes section on http://www.grails.org/WebFlow
			//
			// The following flow scope variables are used to generate
			// wizard tabs. Also see common/_tabs.gsp for more information
			flow.page = 0
			flow.pages = [
				[title: 'Import file'],
				[title: 'Assign properties'],
				[title: 'Check imported data'],
                [title: 'Confirmation'],
				[title: 'Done']
			]
			flow.cancel = true;
			flow.quickSave = true;

            // Get parent entity class name from params or default to 'Study'

            //flow.parentEntityClassName = params.parentEntityClassName ?: 'nl.nbic.animaldb.Investigation'

            flow.parentEntityClassName = grailsApplication.config.parentEntityClassName

            // cache the entities (skipping this doesn't cache them?)
            gdtService.getTemplateEntities().each {
                it
            }

			success()
		}

		// render the main wizard page which immediately
		// triggers the 'next' action (hence, the main
		// page dynamically renders the study template
		// and makes the flow jump to the study logic)
		mainPage {
			render(view: "/gdtImporter/index")
			onRender {
				// let the view know we're in page 1
				flow.page = 1
				success()
			}
			on("next").to "fileImportPage"
		}

		// File import and entity template selection page
		fileImportPage {
			render(view: "_page_one")
			onRender {
				log.info ".entering import wizard"

                flow.page = 1
                flow.gdtImporter_fuzzymatching = "false"

                // Get a list of parent entities the current logged in user owns
                def domainClass                     = AH.application.getDomainClass(flow.parentEntityClassName)
                def parentEntityReferenceInstance   = domainClass.referenceInstance
                //def userParentEntities              //= parentEntityReferenceInstance.findAllWhere(owner: authenticationService.loggedInUser)
                def userParentEntities              = parentEntityReferenceInstance.list()

                flow.gdtImporter_parentEntityReferenceInstance  = parentEntityReferenceInstance
                flow.gdtImporter_userParentEntities             = userParentEntities //.sort{it.title}
                flow.gdtImporter_parentEntityClassName          = domainClass.shortName

				success()
			}

			on("refresh") {

				if (params.entity) {
					flash.gdtImporter_datatemplates = Template.findAllByEntity(gdtService.getInstanceByEntity(params.entity.decodeURL()))
				}

                // Let the view know we are refreshing the page, which means
                // that the JS will reload the Excel preview and the amount of sheets via JSON
                flash.gdtImporter_pageOneRefresh = 'true'


                // Clone all parameters so they are available again after a refresh
                flash.gdtImporter_params = params

				// If the file already exists an "existing*" string is added, but we don't
				// want that after a refresh of the first step in the import wizard, so remove
				// that string
				flash.gdtImporter_params.importfile = params.importfile.replaceAll(/<pre.*?>/,'').replace('</pre>','').replace('existing*','')

				success()
			}.to "fileImportPage"

			on("next") {
                flash.wizardErrors = [:]
				flash.gdtImporter_params = params
				flash.gdtImporter_params.importfile = params.importfile.replaceAll(/<pre.*?>/,'').replace('</pre>','').replace('existing*','')
                flash.gdtImporter_params.pageOneRefresh = 'true'
                flow.gdtImporter_importfile = flash.gdtImporter_params.importfile

                if (!flash.gdtImporter_params.importfile) {
                    log.error('.gdtImporterWizard [fileImportPage] no file specified.')
                    error()
                } else {
                    // TODO: remove this hack
                    flash.gdtImporter_params.importfile = params.importfile.replaceAll(/<pre.*?>/,'').replace('</pre>','').replace('existing*','')
                }

				if (params.entity) {
					flash.gdtImporter_datatemplates = Template.findAllByEntity(gdtService.getInstanceByEntity(params.entity.decodeURL()))
					def gdtImporter_entity_type = gdtService.decryptEntity(params.entity.decodeURL()).toString().split(/\./)
					flow.gdtImporter_entity_type = gdtImporter_entity_type[-1]
				}

                // get selected instance of parent entity through a reference of
                // parent entity's domain class (if applicable).
                if (flow.gdtImporter_entity_type != flow.gdtImporter_parentEntityClassName)
                    println "hier"
    				flow.gdtImporter_parentEntity = flow.gdtImporter_parentEntityReferenceInstance.get(params.parentEntity.id)
				// Trying to import data into an existing parent entity?
				if (flow.gdtImporter_parentEntity && grailsApplication.config.parentEntityHasOwner ) {
					if (flow.gdtImporter_parentEntity.canWrite(authenticationService.getLoggedInUser())) {
						handleFileImportPage(flow, flash, params) ? success() : error()
                    }
					else {
						log.error ".importer wizard wrong permissions"
						this.appendErrorMap(['error': "You don't have the right permissions"], flash.wizardErrors)
						error()
					}
                }
				else {
					handleFileImportPage(flow, flash, params) ? success() : error()
				}

			}.to "propertyAssignmentPage"
		}

		// Property to column assignment page
		propertyAssignmentPage {
			render(view: "_page_two")
			onRender {
				log.info ".import wizard properties page"

                // Delete the uploaded file
                fileService.delete flow.gdtImporter_importfile

				def template = Template.get(flow.gdtImporter_template_id)

                flow.gdtImporter_importmappings = GdtImportMapping.findAllByTemplate(template)

				flow.page = 2
				success()
			}
			on("refresh") {
				def template = Template.get(flow.gdtImporter_template_id)
				flow.gdtImporter_importmappings = GdtImportMapping.findAllByTemplate(template)

				// a name was given to the current property mapping, try to store it
				if (params.mappingname) {
					flash.gdtImporter_columnproperty = params.columnproperty
					propertiesSaveImportMappingPage(flow, flash, params)
				} else // trying to load an existing import mapping
                    if (params.importmapping_id) {
                        propertiesLoadImportMappingPage(flow, flash, params)
                    }

                flow.gdtImporter_fuzzymatching = (params.fuzzymatching == "true") ? "true" : "false"

				success()
			}.to "propertyAssignmentPage"

			on("next") {
				flow.gdtImporter_fuzzymatching = "false"

                flash.wizardErrors = [:]

				if (propertiesPage(flow, flash, params)) {
					success()
				} else {
					log.error ".import wizard, properties are set wrong"
					error()
				}
			}.to "mappingPage"
			on("previous").to "fileImportPage"
		}

		// Mapping page
		mappingPage {
			render(view: "_page_three")
			onRender {
				log.info ".import wizard mapping page"

				flow.page = 3
				success()
			}
			on("refresh") {
				success()
			}.to "mappingPage"
			on("next") {

                def entityList = [], failedFields = [], numberOfUpdatedEntities = 0, numberOfChangedTemplates = 0

                // update the entity list using values from the params
                (entityList, failedFields) = gdtImporterService.setEntityListFieldValuesFromParams(flow.gdtImporter_entityList, params)

                // try to validate the entities
                flow.gdtImporter_failedFields = doValidation(entityList, flow.gdtImporter_parentEntity) + failedFields

                if (flow.gdtImporter_failedFields) {

                    error()

                } else {

                    // if the entities being imported have a preferred identifier
                    // that already exists (within the parent entity, if applicable)
                    // load and update them instead of adding new ones.

                    (entityList, numberOfUpdatedEntities, numberOfChangedTemplates) = gdtImporterService.replaceEntitiesByExistingOnesIfNeeded(entityList, flow.gdtImporter_parentEntity, grailsApplication.config.childEntityParentName)

                    if (numberOfChangedTemplates) {
                        flash.wizardErrors = [:]
                        appendErrorMap(['Warning': "The templates for $numberOfChangedTemplates entities have been changed. This may cause certain fields to be cleared. Please exit the wizard now if you want to prevent this."], flash.wizardErrors)
                    }
                    
                    // overwrite the flow's entityList and store amount of
                    // updated entities in the flow.
                    flow.gdtImporter_entityList = entityList
                    flow.gdtImporter_numberOfUpdatedEntities = numberOfUpdatedEntities

                    success()

                }
			}.to "confirmationPage"
			on("previous").to "propertyAssignmentPage"
		}

        confirmationPage {
            render(view: "_page_four")
            onRender {
				log.info ".import wizard confirmation page"

				flow.page = 4
				success()
            }
            
            on("next") {

                flow.page = 5

                // save the parent entity containing the added entities
                if (flow.gdtImporter_parentEntity) {

                    gdtImporterService.addEntitiesToParentEntity(flow.gdtImporter_entityList, flow.gdtImporter_parentEntity, grailsApplication.config.childEntityParentName)
                    if (!flow.gdtImporter_parentEntity.save()) {
                        log.error ".gdtImporter [mappingPage] could not save parent entity."
                        error()
                    } else success()
                // if the entities we're adding are parent entities
                // themselves, set owner fields and save them individually
                } else{
                    flow.gdtImporter_entityList.each{

                        if (grailsApplication.config.parentEntityHasOwner)
                            it.owner = authenticationService.getLoggedInUser()
                        it.save(failOnError:true)
                    }
                    success()
                }

            }.to "finalPage"

            on("previous").to "mappingPage"
        }

		// render errors
		error {
			render(view: "_error")
			onRender {

				// set page to 4 so that the navigation
				// works (it is disabled on the final page)
				flow.page = 4
			}
			on("tryAgain").to "fileImportPage"
		}

		// last wizard page
		finalPage {
			render(view: "_final_page")
			onRender {
				success()
			}
			onEnd {
				// clean flow scope
				flow.clear()
			}
		}
	}

	/**
     * Collects validation errors. Explicitly checks for unique constraint
     * validation errors for entities belonging to a parent entity. Fills
     *
	 * @param entityList The entity list
     * @param parentEntity The parent entity (if any
     * @return a list of validation errors
	 */
	def doValidation(entityList, parentEntity) {

        flash.wizardErrors = [:]

        def duplicateFailedFields = [], failedValidationFields = [], failedEntities = []

        // explicitly check for unique constraint violations in case of non-
        // parent entities
        if (!parentEntity) {

            duplicateFailedFields = gdtImporterService.detectUniqueConstraintViolations(entityList, parentEntity, grailsApplication.config.childEntityParentName)

            if (duplicateFailedFields)
                appendErrorMap(['duplicates': "Some of the fields that should be unique are duplicates."], flash.wizardErrors)

        }

        (failedValidationFields, failedEntities) = gdtImporterService.validateEntities(entityList, grailsApplication.config.childEntityParentName)

        if (failedValidationFields) {

            // TODO: somehow prevent different error messages about same
            // field from overwriting each other (e.g. 'code' failure
            // for multiple studies)
            failedEntities.each { failedEntity ->
                appendErrors(failedEntity, flash.wizardErrors)
            }

            log.error ".import wizard mapping error, could not validate all entities"
        }

        failedValidationFields + duplicateFailedFields
	}

	/**
	 * Return templates which belong to a certain entity type
	 *
	 * @param entity entity name string (Sample, Subject, Study et cetera)
	 * @return JSON object containing the found templates
	 */
	def ajaxGetTemplatesByEntity = {
		// fetch all templates for a specific entity
		def templates = Template.findAllByEntity(gdtService.getInstanceByEntity(params.entity.decodeURL()))

		// render as JSON
		render templates as JSON
	}

	/**
	 * Handle the file import page.
	 *
	 * @param flow The flow scope
	 * @param params The flow parameters = form data
	 * @returns boolean true if correctly validated, otherwise false
	 */
	boolean handleFileImportPage(flow, flash, params) {

        if (!params['importfile']) {
            log.error ".importer wizard: no file selected."
            this.appendErrorMap(['error': "No file uploaded. Please upload an excel file."], flash.wizardErrors)
            return false
        }

		def importedFile = fileService.get(params['importfile'])
        def workbook

        try {
            workbook = gdtImporterService.getWorkbook(new FileInputStream(importedFile))
        } catch (Exception e) {
            log.error ".importer wizard could not load file: " + e
            this.appendErrorMap(['error': "Wrong file (format), the importer requires an Excel file as input"], flash.wizardErrors)
            return false
        }

		if (params.entity && params.template_id) {

			def entityName = gdtService.decryptEntity(params.entity.decodeURL())

			flow.gdtImporter_template_id = params.template_id
			flow.gdtImporter_sheetIndex = params.sheetIndex.toInteger() // 0 == first sheet
			flow.gdtImporter_headerRowIndex = params.headerRowIndex.toInteger()
			flow.gdtImporter_entityclass = gdtService.getInstanceByEntityName(entityName)
			flow.gdtImporter_entity = gdtService.cachedEntities.find { it.entity == entityName }

            // TODO: change to templateInstance
            flow.gdtImporter_templates = Template.get(flow.gdtImporter_template_id)

            def entityInstance = flow.gdtImporter_entityclass.newInstance(template: flow.gdtImporter_templates)

            // Load raw data
            flow.gdtImporter_dataMatrix = gdtImporterService.getDataMatrix(workbook, flow.gdtImporter_sheetIndex, 0)

			// Get the header from the Excel file using the arguments given in the first step of the wizard
			flow.gdtImporter_header = gdtImporterService.getHeader(
                    flow.gdtImporter_dataMatrix,
                    flow.gdtImporter_headerRowIndex,
                    entityInstance)

			// Remove first row (header)
            flow.gdtImporter_dataMatrix -= flow.gdtImporter_dataMatrix[0]

			flow.gdtImporter_allfieldtypes = "true"

            //fileService.delete params['importfile']

			return true
		}


		log.error ".importer wizard not all fields are filled in"
		appendErrorMap(['error': "Not all fields are filled in, please fill in or select all fields"], flash.wizardErrors)
		return false
	}

	/**
	 * Load an existing import mapping
	 *
`	 * @param flow The flow scope
	 * @param params The flow parameters = form data
	 * @returns return value not used
	 */
	def propertiesLoadImportMappingPage(flow, flash, params) {
		def im = GdtImportMapping.get(params.importmapping_id.toInteger())
		im.refresh()

		im.gdtmappingcolumns.each { gdtMappingColumn ->

			flow.gdtImporter_header[gdtMappingColumn.index.toInteger()] = gdtMappingColumn

		}
	}

	/**
	 * Save the properties as an import mapping.
	 *
 	 * @param flow The flow scope
     * @param params The flow parameters = form data
	 * @returns return value not used
	 */
	def propertiesSaveImportMappingPage(flow, flash, params) {

		def isPreferredIdentifier = false

		// Find actual Template object from the chosen template name
		def template = Template.get(flow.gdtImporter_template_id)

		// Create new GDTImportMapping instance and persist it
		def im = new GdtImportMapping(name: params.mappingname, entity: flow.gdtImporter_entityclass, template: template).save()

		params.columnproperty.index.each { columnindex, property ->

            // Create an actual class instance of the selected entity with the selected template
            // This should be inside the closure because in some cases in the advanced importer, the fields can have different target entities
            //def entityClass = gdtService.getInstanceByEntityName(flow.gdtImporter_header[columnindex.toInteger()].entity.getName())
            def entityObj = flow.gdtImporter_entityclass.newInstance(template: template)

            // Loop through all fields and find the preferred identifier
            entityObj.giveFields().each {
                isPreferredIdentifier = it.preferredIdentifier && (it.name == property)
            }

            // Create new GDTMappingColumn instance
            def mc = new GdtMappingColumn(gdtimportmapping: im,
                name: flow.gdtImporter_header[columnindex.toInteger()].name,
                property: property,
                index: columnindex,
                entityclass: flow.gdtImporter_entityclass,
                templatefieldtype: entityObj.giveFieldType(property),
                dontimport: property == "dontimport",
                identifier: isPreferredIdentifier)

            // Save gdtmappingcolumn
            if (mc.validate()) {
                im.addToGdtmappingcolumns(mc)
            }
            else {
                mc.errors.allErrors.each {
                    println it
                }
            }

            // Save gdtimportmapping
            if (im.validate()) {
                try {
                    im.save(flush: true)
                } catch (Exception e) {
                    //getNextException
                    log.error "importer wizard save gdtimportmapping error: " + e
                }
            }
            else {
                im.errors.allErrors.each {
                    println it
                }
            }
		}
	}

	/**
	 * Handle the property mapping page.
	 *
     * @param flow The flow scope
     * @param params The flow parameters = form data
	 * @returns boolean true if correctly validated, otherwise false
	 */
	boolean propertiesPage(flow, flash, params) {

		// Find actual Template object from the chosen template name
		def template = Template.get(flow.gdtImporter_template_id)

        // Create an actual class instance of the selected entity with the selected template
        def entityInstance = flow.gdtImporter_entityclass.newInstance(template: template)

		params.columnproperty.index.each { columnIndex, property ->

            def column = flow.gdtImporter_header[columnIndex.toInteger()]

			// Store the selected property for this column into the column map for the gdtImporterService
			column.property = property

			// Look up the template field type of the target TemplateField and store it also in the map
			column.templatefieldtype = entityInstance.giveFieldType(property)

			// Is a "Don't import" property assigned to the column?
			column.dontimport = (property == "dontimport")
		}

		// Import the workbook and store the table with entity records and store the failed cells
		def (entityList, failedFields) = gdtImporterService.getDataMatrixAsEntityList(flow.gdtImporter_entity, template,
			flow.gdtImporter_dataMatrix,
			flow.gdtImporter_header)

        if (!entityList) {
            log.error ".importer wizard could not create entities, no mappings made?"
            appendErrorMap(['error': "Could not create entities since no mappings were specified. Please map at least one column."], flash.wizardErrors)
            return false
        }

        // try to validate the entities and combine possible errors with errors
        // from the previous step
        flow.gdtImporter_failedFields = failedFields + doValidation(entityList, flow.gdtImporter_parentEntity)

		flow.gdtImporter_entityList = entityList

        return true
	}

   /**
    * append errors of a particular object to a map
    * @param object
    * @param map linkedHashMap
    * @void
    */
   def appendErrors(object, map) {
       this.appendErrorMap(getHumanReadableErrors(object), map)
   }

   def appendErrors(object, map, prepend) {
       this.appendErrorMap(getHumanReadableErrors(object), map, prepend)
   }

   /**
    * append errors of one map to another map
    * @param map linkedHashMap
    * @param map linkedHashMap
    * @void
    */
   def appendErrorMap(map, mapToExtend) {
       map.each() {key, value ->
           mapToExtend[key] = ['key': key, 'value': value, 'dynamic': false]
       }
   }

   def appendErrorMap(map, mapToExtend, prepend) {
       map.each() {key, value ->
           mapToExtend[prepend + key] = ['key': key, 'value': value, 'dynamic': true]
       }
   }

   /**
    * transform domain class validation errors into a human readable
    * linked hash map
    * @param object validated domain class
    * @return object  linkedHashMap
    */
   def getHumanReadableErrors(object) {
       def errors = [:]
       object.errors.getAllErrors().each() { error ->
           // error.codes.each() { code -> println code }

           // generally speaking g.message(...) should work,
           // however it fails in some steps of the wizard
           // (add event, add assay, etc) so g is not always
           // availably. Using our own instance of the
           // validationTagLib instead so it is always
           // available to us
           errors[error.getArguments()[0]] = validationTagLib.message(error: error)
       }

       return errors
   }

    def getDatamatrixAsJSON = {
        def workbook
        def sheetIndex = 0
        def numberOfSheets = 0

        //TODO: fix annoying existing uploaded prefix issue
        def importfile = params.importfile.replaceAll(/<pre.*?>/,'').replace('</pre>','').replace('existing*','')

        // A sheet has been selected?
        if (params.sheetIndex != "null")
            sheetIndex = params.sheetIndex.toInteger()

        def importedFile = fileService.get(importfile)
        def headerColumns = []

		if (importedFile.exists()) {
			try {
				workbook = gdtImporterService.getWorkbook(new FileInputStream(importedFile))
			} catch (Exception e) {
				log.error ".importer wizard could not load file: " + e
				return false
            }
		}

        // Load all data from the sheet
        def datamatrix = gdtImporterService.getDataMatrix(workbook, sheetIndex, 0)

        //def headerColumns = [[sTitle:"kolom1"], [sTitle:"kolom2"], [sTitle:"kolom3"]]
        datamatrix[0].length.times { headerColumns+= [sTitle:"Column"+it]}

        // Determine number of sheets actually used
        workbook.getNumberOfSheets().times {
            def sheet = workbook.getSheetAt(it)
            if (sheet.getRow(sheet.getFirstRowNum()) != null) numberOfSheets++
        }

        def dataTables = [numberOfSheets:numberOfSheets, iTotalRecords:datamatrix.length, iTotalDisplayRecords:datamatrix.length, aoColumns:headerColumns, aaData: datamatrix]

        render dataTables as JSON
    }
}
