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
import org.apache.poi.ss.usermodel.*
import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.codehaus.groovy.grails.commons.ApplicationHolder as AH
import org.codehaus.groovy.grails.orm.hibernate.validation.UniqueConstraint
import org.codehaus.groovy.grails.validation.NullableConstraint

class GdtImporterService {
    def authenticationService
    def gdtService
    static transactional = true

    /**
	 * @param is input stream representing the (workbook) resource
	 * @return high level representation of the workbook
	 */
	Workbook getWorkbook(InputStream is) {
		WorkbookFactory.create(is)
	}

    /**
     * This method reads the header from the workbook.
     *
	 * @param datamatrix two dimensional datamatrix containing raw read data from Excel
     * @param headerRow row where the header starts
     * @param entityInstance type of entity we are reading
	 * @return header representation as a GdtMappingColumn hashmap
	 */
    def getHeader(String[][] datamatrix, int headerRowIndex, entityInstance = null) {
        def header = []
		def df = new DataFormatter()

		// Loop through all columns from the first row in the datamatrix and try to
        // determine the type of values stored (integer, string, float)
        datamatrix[0].length.times { columnIndex ->

            // Default TemplateFieldType is a String
            def fieldType = TemplateFieldType.STRING

            // Create the GdtMappingColumn object for the current column and store it in the header HashMap
            header[columnIndex] = new GdtMappingColumn(name: datamatrix[0][columnIndex],
							templatefieldtype: fieldType,
							index: columnIndex,
							entityclass: entityInstance.class,
							property: "")
		}

        header
    }

    /**
	 * This method is meant to return a matrix of the rows and columns
	 * used in the preview.
	 *
	 * @param workbook Workbook class object
	 * @param sheetIndex sheet index used
     * @param dataMatrixStartRow row to start reading from
	 * @param count amount of rows of data to read
	 * @return two dimensional array (dataMatrix) of cell values
	 */
    String[][] getDataMatrix(Workbook workbook, int sheetIndex, int count = 0) {
        def sheet = workbook.getSheetAt(sheetIndex)
        def df = new DataFormatter()
		def dataMatrix = []

        count = count ? Math.min(sheet.lastRowNum, count) : sheet.lastRowNum

        // Determine amount of columns
        def columnCount = sheet.getRow(sheet.getFirstRowNum()).getLastCellNum()

		// Walk through all rows
		(sheet.firstRowNum..count).each { rowIndex ->

            def dataMatrixRow = []

            // Get the current row
            def excelRow = sheet.getRow(rowIndex)

            // Excel contains some data?
            if (excelRow)
                columnCount.times { columnIndex ->

                    // Read the cell, even is it a blank
                    def cell = excelRow.getCell(columnIndex, Row.CREATE_NULL_AS_BLANK)
                    // Set the cell type to string, this prevents any kind of formatting

                    // It is a numeric cell?
                    if (cell.cellType == Cell.CELL_TYPE_NUMERIC)
                        // It isn't a date cell?
                        if (!DateUtil.isCellDateFormatted(cell))
                            cell.setCellType(Cell.CELL_TYPE_STRING)

                    switch (cell.cellType) {
                        case Cell.CELL_TYPE_STRING:     dataMatrixRow.add( cell.stringCellValue )
                                                        break
                        case Cell.CELL_TYPE_NUMERIC:    dataMatrixRow.add( df.formatCellValue(cell) )
                                                        break
                        default:                        dataMatrixRow.add( '' )

                    }
                }

            if ( dataMatrixRow.any{it} ) // is at least 1 of the cells non empty?
			    dataMatrix.add(dataMatrixRow)
		}

        dataMatrix
    }

    /**
	 * Method to read data from a Workbook class object and import entities
     * into a list
	 *
     * @param theEntity entity we are trying to read (Subject, Study et cetera)
	 * @param theTemplate Template to use
	 * @param dataMatrix Two-dimensional string array containing excel data
	 * @param mcmap linked hashmap (preserved order) of GdtMappingColumns
	 * @param sheetIndex sheet to use when using multiple sheets
	 * @param dataMatrixRowIndex first row to start with reading the actual data (NOT the header)
	 * @return list containing entities
	 *
	 * @see org.dbnp.gdtimporter.GdtMappingColumn
	 */
	def getDataMatrixAsEntityList(theEntity, theTemplate, dataMatrix, mcmap) {
		def entityList = []
		def errorList = []

		// Walk through all rows and fill the table with entities
		dataMatrix.each { row ->

            // Create an entity record based on a row read from Excel and store the cells which failed to be mapped
			def (entity, error) = createEntity(theEntity, theTemplate, row, mcmap)

            // Add entity to the table if it is not empty
            if (!isEntityEmpty(entity))
                entityList.add(entity)

			// If failed cells have been found, add them to the fieldError list
            // Error contains the entity+identifier+property and the original (failed) value
			if (error) errorList.add(error)
		}

		[entityList, errorList]
	}
    /**
     * @param entityList the list of entities
     * @param parentEntity the parent entity (if any) to which the entities (will) belong
     * @return true if
     */
    def replaceExistingEntitiesHasEqualTemplate(entityList, parentEntity) {
        if (entityList == null) return false

        def preferredIdentifierField = entityList[0].giveDomainFields().find { it.preferredIdentifier }
            if (preferredIdentifierField) {

                def preferredIdentifierValue = entity[preferredIdentifierField.name]

                def c = entity.createCriteria()

                // find an entity with the same parent (in case of a non-parent
                // entity) and preferred identifier value
                def existingEntity = c.get {
                    eq( preferredIdentifierField.name, preferredIdentifierValue )
                    if (parentEntity) eq( "parent", parentEntity )
                }
            }

        existingEntity.template == entityList[0].template
    }

    /**
     * Replaces entities in the list with existing ones if the preferred
     * identifier matches otherwise leaves the entities untouched.
     *
     * @param entityList the list of entities
     * @param parentEntity the parent entity (if any) to which the entities
     *  (will) belong
     * @return [updated entity list, the number of updated entities, the number
     *          of template changes]
     */
    def replaceEntitiesByExistingOnesIfNeeded(entityList, parentEntity) {

        def numberOfUpdatedEntities     = 0
        def numberOfChangedTemplates    = 0

        [entityList.collect { entity ->

            def preferredIdentifierField = entity.giveDomainFields().find { it.preferredIdentifier }
            if (preferredIdentifierField) {

                def preferredIdentifierValue = entity[preferredIdentifierField.name]

                def c = entity.createCriteria()

                // find an entity with the same parent (in case of a non-parent
                // entity) and preferred identifier value
                def existingEntity = c.get {
                    eq( preferredIdentifierField.name, preferredIdentifierValue )
                    if (parentEntity) eq( "parent", parentEntity )
                }

                if (existingEntity) {

                    numberOfUpdatedEntities++

                    // Set the existing entity's template to the user selected template.
                    // they are the same for all entities so we'll get the template for
                    // the first entity
                    if (existingEntity.template != entity.template) {
                        numberOfChangedTemplates++
                        existingEntity.setTemplate(entity.template)
                    }

                    // overwrite all field values of the existing entity
                    entity.giveFields().each { field ->
                        try {
                            existingEntity.setFieldValue(field.name, entity.getFieldValue(field.name))
                        } catch (Exception e) {
                            log.error "Can not set field `" + field.name + " to `" + entity.getFieldValue(field.name) + "`"
                        }
                    }

                    existingEntity
                } else
                    entity
            }
        }, numberOfUpdatedEntities, numberOfChangedTemplates]
    }

    /**
     * Sets field values for a list of entities based on user input via params
     * variable.
     *
     * @param entityList the list of entities to update
     * @param params the params list from the view
     */
    def setEntityListFieldValuesFromParams(entityList, params) {

        def failedFields = []

        entityList.each { entity ->

            entity.giveFields().each { field ->

                def cellName = "entity_${entity.identifier}_${field.escapedName()}"

                def value = params[cellName]

                if (value) {

                    try {

                        entity.setFieldValue( field.name, value, true )

                    } catch(Exception e) {

                        failedFields += [entity: cellName, originalValue: value]

                    }
                }
            }
        }

        [entityList, failedFields]

    }

    /**
	 * Checks whether unique constraints are violated within the entityList.
     * This type of violation can occur in two ways.
     *
     * 1. Entities with same property values already exist
     * 2. Within the entity list there are duplicate values
     *
     * Violations of type 1 will throw an exception but those of type 2 don't
     * (always?). This simply collects all values of the old and new entities
     * within a parent entity and finds duplicates where they are not allowed.
     *
     * see: http://grails.org/doc/latest/ref/Constraints/unique.html
     *
	 * @parentEntity the parent entity
     * @entityList the list of entities to add to parentEntity
     *
     * @return empty list on success, list of errors on failure
	 */
	def detectUniqueConstraintViolations(entityList, parentEntity) {

        def firstEntity     = entityList[0]

        def preferredIdentifierName = firstEntity.giveDomainFields().find { it.preferredIdentifier }?.name

        def failedFields    = []

        def domainClass     = AH.application.getDomainClass(firstEntity.class.name)
        def domainClassReferenceInstance = domainClass.referenceInstance

        // we need all children of parentEntity of same type as the added
        // entities (including ones to be added)
        def childEntities = domainClassReferenceInstance.findAllWhere(parent: parentEntity) + entityList

        // this closure seeks duplicate values of the property with the given
        // name within the childEntities (old and new ones).
        def checkForDuplicates = { propertyName, isNullable ->

            // skip checking existing entities (only new ones) if we're
            // dealing with a preferred identifier. This enables updating.
            def entityProperties = propertyName == preferredIdentifierName ?
                entityList*."$propertyName" : childEntities*."$propertyName"

            def uniques     = [] as Set
            def duplicates  = [] as Set

            // this approach separates the unique from the duplicate entries
            entityProperties.each {
                if (!uniques.add(it)) {

                    // only add to duplicates if null is not allowed and value is null
                    if (!(it == null && isNullable))
                        duplicates.add(it)
                }
            }

            if (duplicates) {

                // Collect all entities with a duplicate value of the unique
                // property. Add corresponding entries to 'failedFields'.
                failedFields += entityList.findAll { it."$propertyName" in duplicates }.collect { duplicate ->

                    [   entity :        "entity_${duplicate.identifier}_$propertyName",
                        originalValue : duplicate[propertyName] ]

                }
            }
        }

        // search through the constrained properties for a 'Unique' constraint
        domainClass.constrainedProperties.each { constrainedProperty ->

            def hasUniqueConstraint = constrainedProperty.value.appliedConstraints.any { appliedConstraint ->

                appliedConstraint instanceof UniqueConstraint

            }

            def isNullable = constrainedProperty.value.appliedConstraints.any { appliedConstraint ->

                appliedConstraint instanceof NullableConstraint && appliedConstraint.isNullable()

            }
            // did we find a 'Unique' constraint? check for duplicate entries
            if (hasUniqueConstraint) {

                checkForDuplicates(constrainedProperty.key, isNullable)
            }

        }

        failedFields

	}

    /**
     *
     * @param entityList
     * @return a list of failed fields and a list of failed entities
     */
    def validateEntities(entityList) {

        def failedFields = []
        def failedEntities = []

        // collect fieldError not related to setting fields, e.g. non-nullable fields
        // that were null.
        entityList.each { entity ->

            if (!entity.validate()) failedEntities.add(entity)

            entity.errors.fieldErrors.each { fieldError ->

                def useError = true

                // if we encounter a parent entity (which has no parent)
                if (!entity.hasProperty('parent')) {

                    // find the preferred identifier
                    def preferredIdentifierField = entity.giveDomainFields().find { it.preferredIdentifier }

                    // ignore this field error when it is the preferred identifier
                    useError = (fieldError.field.toString() != preferredIdentifierField.toString())
                }

                if (useError && fieldError.field != 'parent') // ignore parent errors because we'll add the entities to their parent later
                    failedFields += [entity: "entity_${entity.identifier}_${fieldError.field.toLowerCase().replaceAll("([^a-z0-9])", "_")}", originalValue: fieldError.rejectedValue ?: '']

            }
        }
        [failedFields, failedEntities]
    }

    /**
     * Adds entities from the list to parent entity. Remains agnostic about the
     * specific type of TemplateEntity.
     *
     * @param entityList
     * @param parentEntity
     * @return -
     */
    def addEntitiesToParentEntity(entityList, parentEntity) {

        def firstEntity     = entityList[0]
        def domainClass     = AH.application.getDomainClass(firstEntity.class.name)

        // figure out the collection name via the hasMany property
        def hasMany         = GrailsClassUtils.getStaticPropertyValue(parentEntity.class, 'hasMany')
        def collectionName  = hasMany.find{it.value == domainClass.clazz}.key.capitalize()

        // add the entities one by one to the parent entity (unless it's set already)
        entityList.each { if (!it.parent) parentEntity."addTo$collectionName" it }

    }

    def find

    /**
    * Method to check if all fields of an entity are empty
    *
    * @param theEntity entity object
    */
    def isEntityEmpty(theEntity) {

        theEntity.giveFields().every {

            !theEntity.getFieldValue(it.name)
        }
    }

    /**
	 * This method reads a data row and returns it as filled entity
	 *
     * @param theEntity entity to use
	 * @param theTemplate Template object
	 * @param row list of string values
	 * @param mcmap map containing MappingColumn objects
	 * @return list of entities and list of failed cells
	 */
    def createEntity(theEntity, theTemplate, String[] row, mcmap) {
        def error

		// Initialize the entity with the chosen template
		def entity = gdtService.getInstanceByEntityName(theEntity.entity).
            newInstance(template:theTemplate)

		// Read every cell in the row
		row.eachWithIndex { value, columnIndex ->

            // Get the MappingColumn information of the current cell
			def mc = mcmap[columnIndex]
			// Check if column must be imported
			if (mc != null) if (!mc.dontimport) {
				try {
                    // Format the cell conform the TemplateFieldType
                    value = formatValue(value, mc.templatefieldtype)
                } catch (NumberFormatException nfe) {
                    // Formatting went wrong, so set the value to an empty string
					value = ""
				}

				// Try to set the value for this entity
                try {
                    entity.setFieldValue(mc.property, value, true)
				} catch (Exception iae) {

                    // The entity field value could not be set
                    log.error ".import wizard fieldError could not set property `" + mc.property + "` to value `" + value + "`"

					// Store the fieldError value (might improve this with name of entity instead of "entity_")
                    // as a map containing the entity+identifier+property and the original value which failed
                    error = [ entity: "entity_" + entity.getIdentifier() + "_" + mc.property.toLowerCase(), originalValue: value]
				}
			}
		}

        [entity, error]
    }

    /**
	 * Method to parse a value conform a TemplateFieldType
     *
	 * @param value string containing the value to be formatted
     * @param templateFieldType TemplateFieldType to cast this value to
	 * @return object corresponding to the TemplateFieldType
	 */
	def formatValue(String value, TemplateFieldType templateFieldType) throws NumberFormatException {
		switch (templateFieldType) {
            case TemplateFieldType.LONG:    return Double.valueOf(value.replace(",", ".")).longValue()
            case TemplateFieldType.DOUBLE:  return Double.valueOf(value.replace(",", "."))
		}
        return value.trim()
	}

	static def similarity(l_seq, r_seq, degree = 2) {
		def l_histo = countNgramFrequency(l_seq, degree)
		def r_histo = countNgramFrequency(r_seq, degree)

		dotProduct(l_histo, r_histo) /
				Math.sqrt(dotProduct(l_histo, l_histo) *
				dotProduct(r_histo, r_histo))
	}

	static def countNgramFrequency(sequence, degree) {
		def histo = [:]
		def items = sequence.size()

		for (int i = 0; i + degree <= items; i++) {
			def gram = sequence[i..<(i + degree)]
			histo[gram] = 1 + histo.get(gram, 0)
		}
		histo
	}

	static def dotProduct(l_histo, r_histo) {
		def sum = 0
		l_histo.each { key, value ->
			sum = sum + l_histo[key] * r_histo.get(key, 0)
		}
		sum
	}

	static def stringSimilarity(l_str, r_str, degree = 2) {

		similarity(l_str.toString().toLowerCase().toCharArray(),
				r_str.toString().toLowerCase().toCharArray(),
				degree)
	}

	static def mostSimilar(pattern, candidates, threshold = 0) {
		def topScore = 0
		def bestFit = null

		candidates.each { candidate ->
			def score = stringSimilarity(pattern, candidate)
			if (score > topScore) {
				topScore = score
				bestFit = candidate
			}
		}

		if (topScore < threshold)
			bestFit = null

		bestFit
	}
}