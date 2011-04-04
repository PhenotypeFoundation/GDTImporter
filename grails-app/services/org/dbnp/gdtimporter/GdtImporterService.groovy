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
	 * @param wb high level representation of the workbook
	 * @param sheetIndex sheet to use within the workbook
     * @param headerRow row where the header starts
     * @param datamatrixStart row where the actual data starts
     * @param entityInstance type of entity we are reading
	 * @return header representation as a GdtMappingColumn hashmap
	 */
    def getHeader(Workbook workbook, int sheetIndex, int headerRow, int dataMatrixStart, entityInstance = null) {
        def sheet = workbook.getSheetAt(sheetIndex)
		def dataMatrixRow = sheet.getRow(dataMatrixStart)
		def header = []
		def df = new DataFormatter()

		// Loop through all columns from the first row in the datamatrix and try to
        // determine the type of values stored (integer, string, float)
        (0..dataMatrixRow.getLastCellNum() - 1).each { columnIndex ->

			// Get the current cell type, formatted cell value and the Cell object
            def cellType = dataMatrixRow.getCell(columnIndex, Row.CREATE_NULL_AS_BLANK).getCellType()
			def cellData = df.formatCellValue(dataMatrixRow.getCell(columnIndex))
			def cellObject = dataMatrixRow.getCell(columnIndex)

            // Get the header for the current column
            def columnHeaderCell = sheet.getRow(headerRow + sheet.getFirstRowNum()).getCell(columnIndex)

            // Default TemplateFieldType is a String
            def fieldType = TemplateFieldType.STRING

            // Create the GdtMappingColumn object for the current column and store it in the header HashMap
            header[columnIndex] = new GdtMappingColumn(name: df.formatCellValue(columnHeaderCell),
							templatefieldtype: fieldType,
							index: columnIndex,
							entityclass: entityInstance.class,
							property: "")

			// Check for every CellType
			switch (cellType) {
				case Cell.CELL_TYPE_STRING:
                    // Parse cell value as Double
					def doubleBoolean = true
					fieldType = TemplateFieldType.STRING

                    // Is this string perhaps a Double?
					try {
						formatValue(cellData, TemplateFieldType.DOUBLE)
					} catch (NumberFormatException nfe) { doubleBoolean = false }
					finally {
						if (doubleBoolean) fieldType = TemplateFieldType.DOUBLE
					}

					// Set the TemplateFieldType for the current column
                    header[columnIndex].templatefieldtype = fieldType
					break
				case Cell.CELL_TYPE_NUMERIC:
					fieldType = TemplateFieldType.LONG
					def doubleBoolean = true
					def longBoolean = true

                    // Is this cell really an Integer?
					try {
						Long.valueOf(cellData)
					} catch (NumberFormatException nfe) { longBoolean = false }
					finally {
						if (longBoolean) fieldType = TemplateFieldType.LONG
					}

                    // It's not a Long, perhaps a Double?
					if (!longBoolean)
						try {
							formatValue(cellData, TemplateFieldType.DOUBLE)
						} catch (NumberFormatException nfe) { doubleBoolean = false }
						finally {
							if (doubleBoolean) fieldType = TemplateFieldType.DOUBLE
						}

					// Is the cell object perhaps a Date object?
                    if (DateUtil.isCellDateFormatted(cellObject)) fieldType = TemplateFieldType.DATE

					// Set the TemplateFieldType for the current column
                    header[columnIndex].templatefieldtype = fieldType

					break
				case Cell.CELL_TYPE_BLANK:
                    break
				default:
                    break
			}
		}

        header
    }

    /**
	 * This method is meant to return a matrix of the rows and columns
	 * used in the preview.
	 *
	 * @param workbook Workbook class object
        * @param header header, used to determine width of datamatrix, null if method should detect length internally
	 * @param sheetIndex sheet index used
        * @param dataMatrixStartRow
	 * @param count amount of rows of data to read, starting at dataMatrixStartRow
	 * @return two dimensional array (dataMatrix) of cell values
	 */
    String[][] getDataMatrix(Workbook workbook, header, int sheetIndex, int dataMatrixStartRow, int count = 0) {
        def sheet = workbook.getSheetAt(sheetIndex)
        def df = new DataFormatter()
		def dataMatrix = []

        count = count ? Math.min(sheet.lastRowNum, count) : sheet.lastRowNum

        // Determine length of header
        def headerLength = (!header) ? sheet.getRow(sheet.getFirstRowNum()).getLastCellNum(): header.size()

		// Walk through all rows
		((dataMatrixStartRow + sheet.getFirstRowNum())..count).each { rowIndex ->
			def dataMatrixRow = []
            def excelRow = sheet.getRow(rowIndex)

            if (excelRow)
                headerLength.times { columnIndex ->

                    def cell = excelRow.getCell(columnIndex, Row.CREATE_NULL_AS_BLANK)

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

			// If failed cells have been found, add them to the error list
            // Error contains the entity+identifier+property and the original (failed) value
			if (error) errorList.add(error)
		}

		[entityList, errorList]
	}

    /**
     * Replaces entities in the list with existing ones if the preferred
     * identifier matches otherwise leaves the entities untouched.
     *
     * @param entityList the list of entities
     * @param parentEntity the parent entity (if any) to which the entities
     *  (will) belong
     * @return
     */
    def replaceEntitiesByExistingOnesIfNeeded(entityList, parentEntity) {

        entityList.collect { entity ->

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

                    // overwrite all field values of the existing entity
                    // TODO: make this more efficient
                    entity.giveFields().each { field ->
                        existingEntity.setFieldValue(field.name, entity.getFieldValue(field.name))
                    }

                    existingEntity
                } else
                    entity
            }
        }
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
        def checkForDuplicates = { propertyName ->

            // skip checking existing entities (only new ones) if we're
            // dealing with a preferred identifier. This enables updating.
            def entityProperties = propertyName == preferredIdentifierName ?
                entityList*."$propertyName" : childEntities*."$propertyName"

            def uniques     = [] as Set
            def duplicates  = [] as Set

            // this approach separates the unique from the duplicate entries
            entityProperties.each { uniques.add(it) || duplicates.add(it) }

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

            def hasUniqueConstraint = constrainedProperty.value.appliedConstraints.find { appliedConstraint ->

                appliedConstraint instanceof UniqueConstraint

            }
            // did we find a 'Unique' constraint? check for duplicate entries
            if (hasUniqueConstraint) {

                checkForDuplicates constrainedProperty.key
            }

        }

        failedFields

	}

    def validateEntities(entityList) {

        def failedFields = []
        
        // collect error not related to setting fields, e.g. non-nullable fields
        // that were null.
        entityList.each { entity ->

            entity.validate()

            entity.errors.fieldErrors.each { error ->

                if (error.field != 'parent') // ignore parent errors because we'll add the entities to their parent later
                    failedFields += [entity: "entity_${entity.identifier}_${error.field.toLowerCase().replaceAll("([^a-z0-9])", "_")}", originalValue: error.rejectedValue ?: '']

            }
        }

        failedFields
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
                    println entity.templateStringFields
                    println "setting field $mc.property with value: $value"
				} catch (Exception iae) {

                    // The entity field value could not be set
                    log.error ".import wizard error could not set property `" + mc.property + "` to value `" + value + "`"

					// Store the error value (might improve this with name of entity instead of "entity_")
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