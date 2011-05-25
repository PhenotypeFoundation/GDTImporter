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
import grails.plugins.springsecurity.Secured
import org.codehaus.groovy.grails.commons.ApplicationHolder as AH
import org.hibernate.FlushMode

@Secured(['IS_AUTHENTICATED_REMEMBERED'])
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

            flow.parentEntityClassName = grailsApplication.config.gdtImporter.parentEntityClassName

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
                def userParentEntities              = parentEntityReferenceInstance.findAllWhere(owner: authenticationService.loggedInUser)

                flow.gdtImporter_parentEntityReferenceInstance  = parentEntityReferenceInstance
                flow.gdtImporter_userParentEntities             = userParentEntities.sort{ it.toString() }.each { it.toString()[0..Math.min(70, it.toString().length()-1)]}
                flow.gdtImporter_parentEntityClassName          = domainClass.shortName

				success()
			}

			on("refresh") {

				if (params.entity) {
					flow.gdtImporter_entityTemplates = Template.findAllByEntity(gdtService.getInstanceByEntity(params.entity.decodeURL()))
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
                flow.wizardErrors = [:]
				flash.gdtImporter_params = params
				flash.gdtImporter_params.importfile = params.importfile.replaceAll(/<pre.*?>/,'').replace('</pre>','').replace('existing*','')
                flash.gdtImporter_params.pageOneRefresh = 'true'
                flow.gdtImporter_importfile = flash.gdtImporter_params.importfile
                flow.gdtImporter_dateformat = params.dateformat

                flow.gdtImporter_attachSamplesToSubjects    = (params.attachSamples == 'on')
                flow.gdtImporter_attachEventsToSubjects     = (params.attachEvents == 'on')

                if (!flash.gdtImporter_params.importfile) {
                    log.error('.gdtImporterWizard [fileImportPage] no file specified.')
                    error()
                } else {
                    // TODO: remove this hack
                    flash.gdtImporter_params.importfile = params.importfile.replaceAll(/<pre.*?>/,'').replace('</pre>','').replace('existing*','')
                }

				if (params.entity) {
					flow.gdtImporter_entityTemplates = Template.findAllByEntity(gdtService.getInstanceByEntity(params.entity.decodeURL()))
					def gdtImporter_entity_type = gdtService.decryptEntity(params.entity.decodeURL()).toString().split(/\./)
					flow.gdtImporter_entity_type = gdtImporter_entity_type[-1]
				}

                // get selected instance of parent entity through a reference of
                // parent entity's domain class (if applicable).
                if (flow.gdtImporter_entity_type != flow.gdtImporter_parentEntityClassName)
    				flow.gdtImporter_parentEntity = flow.gdtImporter_parentEntityReferenceInstance.get(params.parentEntity.id)
				// Trying to import data into an existing parent entity?
				if (flow.gdtImporter_parentEntity && grailsApplication.config.gdtImporter.parentEntityHasOwner ) {
					if (flow.gdtImporter_parentEntity.canWrite(authenticationService.getLoggedInUser())) {
						handleFileImportPage(flow, flash, params) ? success() : error()
                    }
					else {
						log.error ".importer wizard wrong permissions"
						this.appendErrorMap(['error': "You don't have the right permissions"], flow.wizardErrors)
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
					 flow.gdtImporter_importmapping = propertiesSaveImportMappingPage(flow, flash, params)
				} else // trying to load an existing import mapping
                    if (params.loadimportmapping_id) {
                        flow.gdtImporter_importmapping = propertiesLoadImportMappingPage(flow, flash, params)
                    } else // trying to delete an existing import mapping
                    if (params.deleteimportmapping_id) {
                        propertiesDeleteImportMappingPage(flow, flash, params)
                    }

                flow.gdtImporter_fuzzymatching = (params.fuzzymatching == "true") ? "true" : "false"

				success()
			}.to "propertyAssignmentPage"

			on("next") {
				flow.gdtImporter_fuzzymatching = "false"

                flow.wizardErrors = [:]

				if (propertiesPage(flow, flash, params)) {
					success()
				} else {
					log.error ".import wizard, properties are set wrong"
					error()
				}
			}.to "mappingPage"
			on("previous"){
                flow.wizardErrors = [:]
            }.to "fileImportPage"
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

                def entityList, failedFields, numberOfUpdatedEntities, numberOfChangedTemplates

                flow.wizardErrors = [:]

                // update the entity list using values from the params
                (entityList, failedFields) = gdtImporterService.setEntityListFieldValuesFromParams(flow.gdtImporter_entityList, params)

                // if the entities being imported have a preferred identifier
                // that already exists (within the parent entity, if applicable)
                // load and update them instead of adding new ones.
                (entityList, numberOfUpdatedEntities, numberOfChangedTemplates) = gdtImporterService.replaceEntitiesByExistingOnesIfNeeded(entityList, flow.gdtImporter_parentEntity, grailsApplication.config.gdtImporter.childEntityParentName)

                if (numberOfUpdatedEntities) {
                    if (flow.gdtImporter_attachSamplesToSubjects) {

                        appendErrorMap(['Error': "There are $numberOfUpdatedEntities samples that exist in the database. In 'attach samples to existing subjects' mode it is not possible to update existing samples."], flow.wizardErrors)

                    } else if (flow.gdtImporter_attachEventsToSubjects) {

                        appendErrorMap(['Error': "There are $numberOfUpdatedEntities events that exist in the database. In 'attach events to existing subjects' mode it is not possible to update existing events."], flow.wizardErrors)

                    }
                }

                if (numberOfChangedTemplates) {
                    appendErrorMap(['Warning': "The templates for $numberOfChangedTemplates entities have been changed. This may cause certain fields to be cleared. Please exit the wizard now if you want to prevent this."], flow.wizardErrors)
                }

                // overwrite the flow's entityList and store amount of
                // updated entities in the flow.
                flow.gdtImporter_entityList = entityList
                flow.gdtImporter_numberOfUpdatedEntities = numberOfUpdatedEntities

                // try to validate the entities
                flow.gdtImporter_failedFields = doValidation(flow, entityList, flow.gdtImporter_parentEntity) + failedFields

                if (flow.gdtImporter_failedFields) {
                    flow.wizardErrors.each {
                        println it
                    }
                    error()

                } else {

                    if (flow.gdtImporter_attachEventsToSubjects) {

                        // In this mode it is expected to have events be repeated
                        // in order to relate the same event to different subjects.
                        // We want a unique list but still be able to link events
                        // and subjects so we'll store a list of indices relating
                        // the original event order to the consolidated events.
                        def (consolidatedEvents, eventIndices) = gdtImporterService.consolidateEntities(flow.gdtImporter_entityList)
                        flow.gdtImporter_entityList     = consolidatedEvents
                        flow.gdtImporter_enventIndices  = eventIndices

                    }

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

                // If we're in 'attach samples to subjects' mode, now it's time to do so
                if (flow.gdtImporter_attachSamplesToSubjects) {

                    gdtImporterService.attachSamplesToSubjects(
                            flow.gdtImporter_entityList,                // samples
                            flow.gdtImporter_subjectNamesToAttach,      // subject names from the sheet representing subject to attach samples to
                            flow.gdtImporter_timePoints,                // time points from the sheet that will be stored in sampling events
                            flow.gdtImporter_template,                  // sample template
                            flow.gdtImporter_parentEntity)

                // Or are we in 'attach events to subjects' mode?
                } else if (flow.gdtImporter_attachEventsToSubjects) {

                    gdtImporterService.attachEventsToSubjects(
                            flow.gdtImporter_entityList,                // events
                            flow.gdtImporter_eventIndices,              // indices relating row number to unique event
                            flow.gdtImporter_subjectNamesToAttach,      // subject names from the sheet representing subject to attach events to
                            flow.gdtImporter_parentEntity)

                // We're in 'standard' mode.
                // If there's a parent entity, add the entities it.
                } else if (flow.gdtImporter_parentEntity) {

                    gdtImporterService.addEntitiesToParentEntity(
                            flow.gdtImporter_entityList,
                            flow.gdtImporter_parentEntity,
                            grailsApplication.config.gdtImporter.childEntityParentName)

                    if (!flow.gdtImporter_parentEntity.save()) {
                        log.error ".gdtImporter [confirmation page] could not save parent entity."
                        error()
                    } else success()

                // When there's not a parent entity defined that means the
                // entities themselves are parent entities. We'll set the owner
                // fields and save them individually.
                } else{
                    flow.gdtImporter_entityList.each{

                        if (grailsApplication.config.gdtImporter.parentEntityHasOwner)
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
			render(view: "_error", plugin:"gdtimporter")
			onRender {

				// set page to 4 so that the navigation
				// works (it is disabled on the final page)
				flow.page = 4
			}
			on("tryAgain").to "fileImportPage"
		}

		// last wizard page
		finalPage {
			render(view: "_final_page", plugin: "gdtimporter")
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
	 * @param flow flow we are in
     * @param entityList The entity list
     * @param parentEntity The parent entity (if any
     * @return a list of validation errors
	 */
	def doValidation(flow, entityList, parentEntity) {

        flow.wizardErrors = [:]

        def duplicateFailedFields = [], failedValidationFields = [], failedEntities = []

        // explicitly check for unique constraint violations in case of non-
        // parent entities
        // BTW: "if (!parentEntity)" seems to validate if the parentEntity is null, so check explicitly for null

        if (parentEntity != null) {
            duplicateFailedFields = gdtImporterService.detectUniqueConstraintViolations(entityList, parentEntity, grailsApplication.config.gdtImporter.childEntityParentName)

            if (duplicateFailedFields)
                appendErrorMap(['duplicates': "Some of the fields that should be unique are duplicates."], flow.wizardErrors)

        }

        (failedValidationFields, failedEntities) = gdtImporterService.validateEntities(entityList, grailsApplication.config.gdtImporter.childEntityParentName)

        if (failedValidationFields) {

            // TODO: somehow prevent different error messages about same
            // field from overwriting each other (e.g. 'code' failure
            // for multiple studies)
            failedEntities.each { failedEntity ->
                appendErrors(failedEntity, flow.wizardErrors)
            }

            log.error ".import wizard mapping error, could not validate all entities"
        }

        // Return all failed fields which have an original name (!= empty)
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
            this.appendErrorMap(['error': "No file uploaded. Please upload an excel file."], flow.wizardErrors)
            return false
        }

		def importedFile = fileService.get(params['importfile'])
        def workbook

        try {
            workbook = gdtImporterService.getWorkbook(new FileInputStream(importedFile))

            // Determine the amount of sheets and put them in the flow scope
            def sheets = []
            workbook.getNumberOfSheets().times {
                sheets.add (it+1)
            }
            flow.gdtImporter_sheets = sheets

        } catch (Exception e) {
            log.error ".importer wizard could not load file: " + e
            this.appendErrorMap(['error': "Wrong file (format), the importer requires an Excel file as input"], flow.wizardErrors)
            return false
        }

		if (params.entity && params.template_id) {

			def entityName = gdtService.decryptEntity(params.entity.decodeURL())

			flow.gdtImporter_template_id = params.template_id
			flow.gdtImporter_sheetIndex = params.sheetIndex.toInteger() // 0 == first sheet
			flow.gdtImporter_headerRowIndex = params.headerRowIndex.toInteger()
			flow.gdtImporter_entityclass = gdtService.getInstanceByEntityName(entityName)
			flow.gdtImporter_entity = gdtService.cachedEntities.find { it.entity == entityName }

            flow.gdtImporter_template = Template.get(flow.gdtImporter_template_id)

            def entityInstance = flow.gdtImporter_entityclass.newInstance(template: flow.gdtImporter_template)

            // Load raw data
            flow.gdtImporter_dataMatrix = gdtImporterService.getDataMatrix(workbook, flow.gdtImporter_sheetIndex, 0)

			// Get the header from the Excel file using the arguments given in the first step of the wizard
			flow.gdtImporter_header = gdtImporterService.getHeader(
                    flow.gdtImporter_dataMatrix,
                    flow.gdtImporter_headerRowIndex,
                    entityInstance)

			// Remove all rows before and including the header to keep only the "real" data
            flow.gdtImporter_dataMatrix -= flow.gdtImporter_dataMatrix[0..flow.gdtImporter_headerRowIndex]

			flow.gdtImporter_allfieldtypes = "true"

            // if the user wants to add the samples or events to existing
            // subjects make it possible to select the required extra options
            if (flow.gdtImporter_attachSamplesToSubjects) {

                flow.gdtImporter_extraOptions = [
                        [preferredIdentifier: false, name: 'Subject name', unit: false],
                        [preferredIdentifier: false, name: 'Timepoint', unit: false]
                ]

            } else if (flow.gdtImporter_attachEventsToSubjects) {

                flow.gdtImporter_extraOptions = [
                        [preferredIdentifier: false, name: 'Subject name', unit: false],
                ]

            }

			return true
		}

		log.error ".importer wizard not all fields are filled in"
		appendErrorMap(['error': "Not all fields are filled in, please fill in or select all fields"], flow.wizardErrors)
		return false
	}

	/**
	 * Load an existing gdt import mapping
	 *
`	 * @param flow The flow scope
	 * @param params The flow parameters = form data
	 * @returns return GdtImportMapping object
	 */
	def propertiesLoadImportMappingPage(flow, flash, params) {
		def im = GdtImportMapping.get(params.loadimportmapping_id.toInteger())
		im.refresh()

		im.gdtmappingcolumns.each { gdtMappingColumn ->
			flow.gdtImporter_header[gdtMappingColumn.index.toInteger()] = gdtMappingColumn
		}

        im
	}

    /**
	 * Delete an existing import mapping
	 *
`	 * @param flow The flow scope
	 * @param params The flow parameters = form data
	 * @returns return ImportMapping object
	 */
	def propertiesDeleteImportMappingPage(flow, flash, params) {
        def importmapping_id = params.deleteimportmapping_id.toLong()

        // Manual deletion is necessary; the delete isn't performed by Hibernate directly and this
        // results in deleted items being still visible in the dropdown box
        // Delete all GdtMappingColumn objects belonging to this GdtImportMapping (cascaded delete doesn't work)
        GdtMappingColumn.executeUpdate("DELETE GdtMappingColumn WHERE gdtimportmapping_id= ?", [importmapping_id])

        // Delete the GdtImportMapping
        GdtImportMapping.executeUpdate("DELETE GdtImportMapping WHERE id = ?",[importmapping_id])
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

        im
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

        // check whether the user entered the required fields when in 'attach samples/events to subjects' mode
        if (flow.gdtImporter_attachSamplesToSubjects || flow.gdtImporter_attachEventsToSubjects) {

            def subjectNameColumnNumber = flow.gdtImporter_header.findIndexOf{it.property == "Subject name"}

            flow.gdtImporter_header[subjectNameColumnNumber].dontimport = true

            // store subject names in the flow
            flow.gdtImporter_subjectNamesToAttach = flow.gdtImporter_dataMatrix.collect{it[subjectNameColumnNumber]}

            def studySubjectNames = flow.gdtImporter_parentEntity.subjects*.name

            def missingSubjectNames = flow.gdtImporter_subjectNamesToAttach.clone().unique()
            missingSubjectNames.removeAll( studySubjectNames )

            if (missingSubjectNames) {
                log.error ".importer wizard - not all subjects are present in study"
                appendErrorMap(['error': "Some subject names could not be found in the study. Please compare the subjects from the selected study with the subject names from the excel sheet. Missing subjects: ${missingSubjectNames.join(', ')}."], flow.wizardErrors)
                return false
            }
        }

        if (flow.gdtImporter_attachSamplesToSubjects) {

            // store timepoints in the flow
            def timepointColumnNumber   = flow.gdtImporter_header.findIndexOf{it.property == "Timepoint"}
            flow.gdtImporter_header[timepointColumnNumber].dontimport = true

            flow.gdtImporter_timePoints = flow.gdtImporter_dataMatrix.collect{it[timepointColumnNumber]}
            // TODO: check timepoints for valid values

            // check whether user specified subject names and timepoints
            if (!(flow.gdtImporter_subjectNamesToAttach && flow.gdtImporter_timePoints )) {
                log.error ".importer wizard - could not find both subject name and timepoint selected in the header columns."
                appendErrorMap(['error': "When attaching samples to subjects you need to select both \"Subject name\" and \"Timepoint\" in the header columns."], flow.wizardErrors)
                return false
            }

            // search the parent entity for samples with the same name
            def sampleNameColumnNumber  = flow.gdtImporter_header.findIndexOf{it.property == "name"}
            def existingSamples         = flow.gdtImporter_dataMatrix.collect{it[sampleNameColumnNumber]}.findAll{ sampleName ->
                flow.gdtImporter_parentEntity.samples.find{it.name == sampleName}
            }
            // Don't allow to continue when there are pre-existing samples in the sheet
            if (existingSamples) {
                log.error ".importer wizard - there are existing samples in the database which is not allowed in this mode."
                appendErrorMap(['error': "In the excel sheet there are ${existingSamples.size()} which correspond to samples in the database. When attaching samples to existing subjects, it is not possible to update samples. Importer can not continue."], flow.wizardErrors)
                return false
            }

        }

		// Import the workbook and store the table with entity records and store the failed cells
		def (entityList, failedFields) = gdtImporterService.getDataMatrixAsEntityList(flow.gdtImporter_entity, template,
			flow.gdtImporter_dataMatrix,
			flow.gdtImporter_header,
            flow.gdtImporter_dateformat)

        if (!entityList) {
            log.error ".importer wizard could not create entities, no mappings made?"
            appendErrorMap(['error': "Could not create entities since no mappings were specified. Please map at least one column."], flow.wizardErrors)
            return false
        }

        // try to validate the entities and combine possible errors with errors
        // from the previous step
        // Concatenate failed fields and validated fields from the validation method
        def failedFieldsList = failedFields + doValidation(flow, entityList, flow.gdtImporter_parentEntity)

        // Remove redundant entities where original value is empty and same entity also contains an invalid value
        flow.gdtImporter_failedFields = failedFieldsList.groupBy{it.entity}.collect{
            [entity: it.key, originalValue: (it.value.size() > 1) ? it.value.originalValue.find{it} : it.value.originalValue[0]]
        }

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
		def importfile = params.importfile.replaceAll(/<pre.*?>/, '').replace('</pre>', '').replace('existing*', '')

		// A sheet has been selected?
		if (params.sheetIndex != "null")
			sheetIndex = params.sheetIndex.toInteger()

		def importedFile = fileService.get(importfile)
		def headerColumns = []

		if (importedFile.exists()) {
			try {
				workbook = gdtImporterService.getWorkbook(new FileInputStream(importedFile))
			} catch (Exception e) {
				log.error ".importer wizard could not load file, exception: " + e
				return false
			}
		}

		// Load all data from the sheet
		def datamatrix = gdtImporterService.getDataMatrix(workbook, sheetIndex, 0)

		//def headerColumns = [[sTitle:"kolom1"], [sTitle:"kolom2"], [sTitle:"kolom3"]]
		def dataTables = [:]
		if (datamatrix) {
			datamatrix[0].length.times { headerColumns += [sTitle: "Column" + it]}

			// Determine number of sheets actually used
			workbook.getNumberOfSheets().times {
				def sheet = workbook.getSheetAt(it)
				if (sheet.getRow(sheet.getFirstRowNum()) != null) numberOfSheets++
			}

			dataTables = [numberOfSheets: numberOfSheets, iTotalRecords: datamatrix.length, iColumns: datamatrix.length, iTotalDisplayRecords: datamatrix.length, aoColumns: headerColumns, aaData: datamatrix]
		}
		render dataTables as JSON
    }
}
