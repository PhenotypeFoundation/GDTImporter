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

import dbnp.studycapturing.*
import org.dbnp.gdt.*
import grails.converters.JSON
import org.codehaus.groovy.grails.plugins.web.taglib.ValidationTagLib
import grails.plugins.springsecurity.Secured

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
				[title: 'Done']
			]
			flow.cancel = true;
			flow.quickSave = true;

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
			on("next").to "pageOne"
		}

		// File import and entitie template selection page
		pageOne {
			render(view: "_page_one")
			onRender {
				log.info ".entering import wizard"

				flow.page = 1

                // Get a list of Studies the current logged in user owns
                // TODO: make more generic using some sort of parentEntity class, now GDTImporter depends on GSCF
                flow.studies = Study.findAllWhere(owner: authenticationService.getLoggedInUser())
			
				flow.importer_fuzzymatching = "false"

				success()
			}

			on("refresh") {

				if (params.entity) {
					flash.importer_datatemplates = Template.findAllByEntity(gdtService.getInstanceByEntity(params.entity.decodeURL()))
				}

				flash.importer_params = params

				// If the file already exists an "existing*" string is added, but we don't
				// want that after a refresh of the first step in the import wizard, so remove
				// that string
				flash.importer_params.importfile = params.importfile.replace('existing*', '')

				success()
			}.to "pageOne"

			on("next") {
				flash.wizardErrors = [:]
				flash.importer_params = params
				flash.importer_params.importfile = params.importfile.replace('existing*', '')

				if (params.entity) {
					flash.importer_datatemplates = Template.findAllByEntity(gdtService.getInstanceByEntity(params.entity.decodeURL()))
					def importer_entity_type = gdtService.decryptEntity(params.entity.decodeURL()).toString().split(/\./)
					flow.importer_entity_type = importer_entity_type[importer_entity_type.size()-1]
				}

				// Study selected?
				flow.importer_study = (params.study) ? Study.get(params.study.id.toInteger()) : null

				// Trying to import data into an existing study?
				if (flow.importer_study)
					if (flow.importer_study.canWrite(authenticationService.getLoggedInUser()))
						fileImportPage(flow, flash, params) ? success() : error()
					else {
						log.error ".importer wizard wrong permissions"
						this.appendErrorMap(['error': "You don't have the right permissions"], flash.wizardErrors)
						error()
					}
				else {
					fileImportPage(flow, flash, params) ? success() : error()
				}

				// put your bussiness logic (if applicable) in here
			}.to "pageTwo"
		}

		// Property to column assignment page
		pageTwo {
			render(view: "_page_two")
			onRender {
				log.info ".import wizard properties page"

				def template = Template.get(flow.importer_template_id)

				flow.importer_importmappings = GDTImportMapping.findAllByTemplate(template)

				flow.page = 2
				success()
			}
			on("refresh") {
				def template = Template.get(flow.importer_template_id)
				flow.importer_importmappings = GDTImportMapping.findAllByTemplate(template)

				// a name was given to the current property mapping, try to store it
				if (params.mappingname) {
					flash.importer_columnproperty = params.columnproperty
					propertiesSaveImportMappingPage(flow, flash, params)
				} else // trying to load an existing import mapping
				if (params.importmapping_id) {
					propertiesLoadImportMappingPage(flow, flash, params)
				}

				if (params.fuzzymatching == "true")
					flow.importer_fuzzymatching = "true" else
					flow.importer_fuzzymatching = "false"

				success()
			}.to "pageTwo"

			on("next") {
				flow.importer_fuzzymatching = "false"
				if (propertiesPage(flow, flash, params)) {
					success()
				} else {
					log.error ".import wizard, properties are set wrong"
					error()
				}
			}.to "pageThree"
			on("previous").to "pageOne"
		}

		// Mapping page
		pageThree {
			render(view: "_page_three")
			onRender {
				log.info ".import wizard mapping page"

				flow.page = 3
				success()
			}
			on("refresh") {
				success()
			}.to "pageThree"
			on("next") {
				if (mappingsPage(flow, flash, params)) {
					flow.page = 4
					success()
				} else {
					log.error ".import wizard mapping error, could not validate all entities"
					error()
				}
			}.to "save"
			on("previous").to "pageTwo"
		}

		// Imported data overview page
		pageFour {
			render(view: "_page_four")
			onRender {

				flow.page = 4
				success()
			}
			on("next") {
				if (importedPage(flow, params)) {
					flow.page = 4
					success()
				} else {
					log.error ".import wizard imported error, something went wrong showing the imported entities"
					error()
				}
			}.to "save"
			on("previous").to "pageThree"
		}

		// Save the imported data
        save {
            action {
				// here you can validate and save the
				// instances you have created in the
				// ajax flow.

				// Always delete the uploaded file in the save step to be sure it doesn't reside there anymore
				fileService.delete(flow.importer_importedfile)

				// Save all entities
                try {
                    gdtImporterService.saveEntities(flow, authenticationService, log)
                } catch (Exception e) {

                    this.appendErrorMap(['error': e.message], flash.wizardErrors)
                    pageThree()
                }
			}
			on("pageThree").to "pageThree"
			on(Exception).to "error"
			on("success").to "finalPage"
		}

		// render errors
		error {
			render(view: "_error")
			onRender {

				// set page to 4 so that the navigation
				// works (it is disabled on the final page)
				flow.page = 4
			}
			on("next").to "save"
			on("previous").to "pageFour"
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
	 * @param Map LocalAttributeMap (the flow scope)
	 * @param Map GrailsParameterMap (the flow parameters = form data)
	 * @returns boolean true if correctly validated, otherwise false
	 */
	boolean fileImportPage(flow, flash, params) {
		def importedfile = fileService.get(params['importfile'])
		flow.importer_importedfile = params['importfile']

		if (importedfile.exists()) {
			try {
				session.importer_workbook = gdtImporterService.getWorkbook(new FileInputStream(importedfile))
			} catch (Exception e) {
				log.error ".importer wizard could not load file: " + e
				this.appendErrorMap(['error': "Wrong file (format), the importer requires an Excel file as input"], flash.wizardErrors)
				return false
			}
		}

		if (params.entity && params.template_id) {

			try {
				session.importer_workbook = gdtImporterService.getWorkbook(new FileInputStream(importedfile))
			} catch (Exception e) {
				log.error ".importer wizard could not load file: " + e
				this.appendErrorMap(['error': "Excel file required as input"], flash.wizardErrors)
				return false
			}

			def selectedentities = []

			def entityName = gdtService.decryptEntity(params.entity.decodeURL())
			def entityClass = gdtService.getInstanceByEntityName(entityName)

			// Initialize some session variables
			//flow.importer_workbook = wb // workbook object must be serialized for this to work

			flow.importer_template_id = params.template_id
			flow.importer_sheetindex = params.sheetindex.toInteger() - 1 // 0 == first sheet
			flow.importer_datamatrix_start = params.datamatrix_start.toInteger() - 1 // 0 == first row
			flow.importer_headerrow = params.headerrow.toInteger()
			flow.importer_entityclass = entityClass
			flow.importer_entity = gdtService.cachedEntities.find { it.entity == entityName }

			// Get the header from the Excel file using the arguments given in the first step of the wizard
			flow.importer_header = gdtImporterService.getHeader(session.importer_workbook,
				flow.importer_sheetindex,
				flow.importer_headerrow,
				flow.importer_datamatrix_start,
				entityClass)

			// Load a preview of the data
            flow.importer_datamatrix = gdtImporterService.getDatamatrix(
				session.importer_workbook, flow.importer_header,
				flow.importer_sheetindex,
				flow.importer_datamatrix_start,
				5)

			flow.importer_templates = Template.get(flow.importer_template_id)
			flow.importer_allfieldtypes = "true"

			return true
		}


		log.error ".importer wizard not all fields are filled in"
		this.appendErrorMap(['error': "Not all fields are filled in, please fill in or select all fields"], flash.wizardErrors)
		return false
	}

	/**
	 * Load an existing import mapping
	 *
	 * @param Map LocalAttributeMap (the flow scope)
	 * @param Map GrailsParameterMap (the flow parameters = form data)
	 * @returns boolean true if correctly validated, otherwise false
	 */
	boolean propertiesLoadImportMappingPage(flow, flash, params) {
		def im = ImportMapping.get(params.importmapping_id.toInteger())
		im.refresh()

		im.mappingcolumns.each { gdtmappingcolumn ->
			//def mc = new MappingColumn()
			//mc.properties = mappingcolumn.properties

			flow.importer_header[gdtmappingcolumn.index.toInteger()] = gdtmappingcolumn
		}
	}

	/**
	 * Save the properties as an import mapping.
	 *
	 * @param Map LocalAttributeMap (the flow scope)
	 * @param Map GrailsParameterMap (the flow parameters = form data)
	 * @returns boolean true if correctly validated, otherwise false
	 */
	boolean propertiesSaveImportMappingPage(flow, flash, params) {
		flash.wizardErrors = [:]
		def isPreferredIdentifier = false

		// Find actual Template object from the chosen template name
		def template = Template.get(flow.importer_template_id)

		// Create new GDTImportMapping instance and persist it
		def im = new GDTImportMapping(name: params.mappingname, entity: flow.importer_entityclass, template: template).save()

		params.columnproperty.index.each { columnindex, property ->
			// Create an actual class instance of the selected entity with the selected template
			// This should be inside the closure because in some cases in the advanced importer, the fields can have different target entities
			//def entityClass = gdtService.getInstanceByEntityName(flow.importer_header[columnindex.toInteger()].entity.getName())
			def entityObj = flow.importer_entityclass.newInstance(template: template)

			def dontimport = (property == "dontimport") ? true : false

			// Loop through all fields and find the preferred identifier
			entityObj.giveFields().each {
				isPreferredIdentifier = (it.preferredIdentifier && (it.name == property)) ? true : false
			}

			// Create new GDTMappingColumn instance
			def mc = new GDTMappingColumn(gdtimportmapping: im,
				name: flow.importer_header[columnindex.toInteger()].name,
				property: property,
				index: columnindex,
				entityclass: flow.importer_entityclass,
				templatefieldtype: entityObj.giveFieldType(property),
				dontimport: dontimport,
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
	 * @param Map LocalAttributeMap (the flow scope)
	 * @param Map GrailsParameterMap (the flow parameters = form data)
	 * @returns boolean true if correctly validated, otherwise false
	 */
	boolean propertiesPage(flow, flash, params) {
		flash.wizardErrors = [:]

		// Find actual Template object from the chosen template name
		def template = Template.get(flow.importer_template_id)

		params.columnproperty.index.each { columnindex, property ->
			// Create an actual class instance of the selected entity with the selected template
			// This should be inside the closure because in some cases in the advanced importer, the fields can have different target entities
			def entityClass = Class.forName(flow.importer_header[columnindex.toInteger()].entityclass.getName(), true, this.getClass().getClassLoader())
			def entityObj = entityClass.newInstance(template: template)

			// Store the selected property for this column into the column map for the gdtImporterService
			flow.importer_header[columnindex.toInteger()].property = property

			// Look up the template field type of the target TemplateField and store it also in the map
			flow.importer_header[columnindex.toInteger()].templatefieldtype = entityObj.giveFieldType(property)

			// Is a "Don't import" property assigned to the column?
			flow.importer_header[columnindex.toInteger()].dontimport = (property == "dontimport") ? true : false

			//if it's an identifier set the mapping column true or false
			entityObj.giveFields().each {
				(it.preferredIdentifier && (it.name == property)) ? flow.importer_header[columnindex.toInteger()].identifier = true : false
			}
		}

		// Import the workbook and store the table with entity records and store the failed cells
		def (table, failedFields) = gdtImporterService.getDatamatrixAsEntityList(flow.importer_entity, template,
			session.importer_workbook,
			flow.importer_sheetindex,
			flow.importer_datamatrix_start,
			flow.importer_header)

		flow.importer_importeddata = entityList

		// loop through all entities to validate them and add them to wizardErrors flash when invalid
		/*table.each { record ->
					record.each { entity ->
						if (!entity.validate()) {
						this.appendErrors(entity, flash.wizardErrors, 'entity_' + entity.getIdentifier() + '_')
						}
					}
				}*/

		flow.importer_failedFields = failedFields

		return true
	}

	/**
	 * Handle the mapping page.
	 *
	 * @param Map LocalAttributeMap (the flow scope)
	 * @param Map GrailsParameterMap (the flow parameters = form data)
	 * @returns boolean true if correctly validated, otherwise false
	 */
	boolean mappingsPage(flow, flash, params) {
		flash.wizardErrors = [:]
		flow.importer_invalidentities = 0

		flow.importer_importeddata.each { entity ->
				def invalidfields = 0

				// Set the fields for this entity by retrieving values from the params
				entity.giveFields().each { field ->
                    def entityField = "entity_" + entity.getIdentifier() + "_" + field.escapedName()
                    println "entityfield=" + entityField
       
					// field is a date field, try to set it with the value, if someone enters a non-date value it throws
					// an error, this should be caught to prevent a complete breakdown
					if (field.type == org.dbnp.gdt.TemplateFieldType.DATE) {
						try {
							entity.setFieldValue(field.toString(), params[entityField])
						} catch (Exception e) {
							log.error ".importer wizard could not set date field with value: " +
								params[entityField]
						}
					} else

					// field of type ontology and value "#invalidterm"?
					if (field.type == org.dbnp.gdt.TemplateFieldType.ONTOLOGYTERM &&
						params[entityField] == "#invalidterm")
                        {
                            invalidfields++
					} else
					if (field.type == org.dbnp.gdt.TemplateFieldType.ONTOLOGYTERM &&
						params[entityField] != "#invalidterm") {
                        if (entity) removeFailedField(flow.importer_failedFields, entityField)
						entity.setFieldValue(field.toString(), params[entityField])
					}
					else

					if (field.type == org.dbnp.gdt.TemplateFieldType.STRINGLIST &&
						params[entityField] != "#invalidterm") {
						println "TEST1" + params[entityField]
                        if (entity) removeFailedField(flow.importer_failedFields, entityField)
						entity.setFieldValue(field.toString(), params[entityField])
					} else
					if (field.type == org.dbnp.gdt.TemplateFieldType.STRINGLIST &&
						params[entityField] == "#invalidterm"
					) {
						invalidfields++
					} else

						entity.setFieldValue(field.toString(), params[entityField])
				}

				// Determine entity class and add a parent (defined as Study in first step of wizard)
				switch (entity.getClass()) {
					case [Subject, Sample, Event]: entity.parent = flow.importer_study
				}

				// Try to validate the entity now all fields have been set
				if (!entity.validate() || invalidfields) {
					flow.importer_invalidentities++

					// add errors to map
					this.appendErrors(entity, flash.wizardErrors, "entity_" + entity.getIdentifier() + "_")

					entity.errors.getAllErrors().each() {
						log.error ".import wizard imported validation error:" + it
					}
				} else {
					//removeFailedCell(flow.importer_failedcells, entity)
				} // end else if

		} // end of list

		return (flow.importer_invalidentities == 0) ? true : false
	} // end of method

	/**
     * Method to remove a failed field from the failed fields list
     *
     * @param failedFieldsList list of failed fields
	 * @param failedField field to remove from the failed fields list
	 */
	def removeFailedField(failedFieldsList, failedField) {
        failedFieldsList = failedFieldsList.findAll{ it.entity!=failedField }
	}

	/**
	 * Handle the imported entities page.
	 *
	 * @param Map LocalAttributeMap (the flow scope)
	 * @param Map GrailsParameterMap (the flow parameters = form data)
	 * @returns boolean true if correctly validated, otherwise false
	 */
	boolean importedPage(flow, params) {
		return true
	}

//    /**
//     *
//     * @param flow should contain importer_study and importer_importeddata
//     * @param params
//     * @return
//     */
//	boolean saveEntities(flow, params) {
//		//def (validatedSuccesfully, updatedEntities, failedToPersist) =
//		try {
////			gdtImporterService.saveEntities(flow.importer_study, flow.importer_importeddata, authenticationService, log)
//			gdtImporterService.saveEntities(flow, authenticationService, log)
//		} catch (Exception e) {
//			log.error ".import wizard saveEntities error\n" + e.dump()
//			return false
//		}
//
//		//flow.importer_validatedsuccesfully = validatedSuccesfully
//		//flow.importer_failedtopersist = failedToPersist
//		//flow.imported_updatedentities = updatedEntities
//		//flow.importer_totalrows = flow.importer_importeddata.size
//		//flow.importer_referer = ""
//
//		return true
//	}

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
}
