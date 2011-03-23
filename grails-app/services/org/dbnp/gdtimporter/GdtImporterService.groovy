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
//import dbnp.studycapturing.*
import org.apache.poi.ss.usermodel.*
import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.codehaus.groovy.grails.commons.ApplicationHolder
import org.codehaus.groovy.grails.orm.hibernate.validation.UniqueConstraint
import org.codehaus.groovy.grails.orm.hibernate.metaclass.FindAllPersistentMethod

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
     * @param theEntity type of entity we are reading
	 * @return header representation as a GDTMappingColumn hashmap
	 */
    def getHeader(Workbook workbook, int sheetIndex, int headerRow, int datamatrixStart, theEntity = null) {
        def sheet = workbook.getSheetAt(sheetIndex)
		def datamatrixRow = sheet.getRow(datamatrixStart)		
		def header = []
		def df = new DataFormatter()
		
        // By default the property is a String anyway; the property is set in the GUI
        // and is an alias for a fieldname chosen from a template
        def property = new String()

		// Loop through all columns from the first row in the datamatrix and try to
        // determine the type of values stored (integer, string, float)
        (0..datamatrixRow.getLastCellNum() - 1).each { columnIndex ->
          
			// Get the current cell type, formatted cell value and the Cell object
            def cellType = datamatrixRow.getCell(columnIndex, Row.CREATE_NULL_AS_BLANK).getCellType()
			def cellData = df.formatCellValue(datamatrixRow.getCell(columnIndex))
			def cellObject = datamatrixRow.getCell(columnIndex)
			
            // Get the header for the current column
            def columnHeaderCell = sheet.getRow(headerRow + sheet.getFirstRowNum()).getCell(columnIndex)
			
            // Default TemplateFieldType is a String
            def fieldType = TemplateFieldType.STRING
            
            // Create the GDTMappingColumn object for the current column and store it in the header HashMap
            header[columnIndex] = new GDTMappingColumn(name: df.formatCellValue(columnHeaderCell),
							templatefieldtype: fieldType,
							index: columnIndex,
							entityclass: theEntity,
							property: property);

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
                    // Set the TemplateFieldType for the current column
                    header[columnIndex].templatefieldtype = fieldType							
					
                    break
				default:
					// Set the TemplateFieldType for the current column
                    header[columnIndex].templatefieldtype = fieldType							
					
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
	 * @param sheetIndex sheet index used
     * @param datamatrixStartRow
	 * @param count amount of rows of data to read, starting at datamatrixStartRow
	 * @return two dimensional array (datamatrix) of cell values
	 */
    String[][] getDatamatrix(Workbook workbook, header, int sheetIndex, int datamatrixStartRow, int count) {
        def sheet = workbook.getSheetAt(sheetIndex)
        def df = new DataFormatter()
		def datamatrix = []

		count = (count < sheet.getLastRowNum()) ? count : sheet.getLastRowNum()

		// Walk through all rows
		((datamatrixStartRow + sheet.getFirstRowNum())..count).each { rowIndex ->
			def datamatrixRow = []
            def excelRow = sheet.getRow(rowIndex)

            if (excelRow)
                (0..header.size() - 1).each { columnIndex ->

                    def cell = excelRow.getCell(columnIndex, Row.CREATE_NULL_AS_BLANK)

                    switch (cell.getCellType()) {
                        case Cell.CELL_TYPE_STRING :    datamatrixRow.add( cell.getStringCellValue() )
                                                        break
                        case Cell.CELL_TYPE_NUMERIC:    datamatrixRow.add( df.formatCellValue(cell) )
                                                        break
                    }
                }
			datamatrix.add(datamatrixRow)
		}

		datamatrix
    }

    /**
	 * Method to read data from a Workbook class object and import entities
     * into a list
	 *
     * @param theEntity entity we are trying to read (Subject, Study et cetera)
	 * @param theTemplate Template to use
	 * @param workbook POI horrible spreadsheet formatted Workbook class object
	 * @param mcmap linked hashmap (preserved order) of GDTMappingColumns
	 * @param sheetIndex sheet to use when using multiple sheets
	 * @param datamatrixRowIndex first row to start with reading the actual data (NOT the header)
	 * @return list containing entities
	 *
	 * @see org.dbnp.gdtimporter.GDTMappingColumn
	 */
	def getDatamatrixAsEntityList(theEntity, theTemplate, Workbook workbook, int sheetIndex, int datamatrixRowIndex, mcmap) {
		def sheet = workbook.getSheetAt(sheetIndex)
		def entityList = []
		def errorList = []

		// Walk through all rows and fill the table with entities
		(datamatrixRowIndex..sheet.getLastRowNum()).each { i ->
			
            // Create an entity record based on a row read from Excel and store the cells which failed to be mapped
			def (entity, error) = createEntity(theEntity, theTemplate, sheet.getRow(i), mcmap)
            
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
	 * Method to store a list containing entities.
	 *
     * @flow should contain importer_parentEntity and importer_importedData
     * @param authenticationService authentication service
     *
     * @return empty list on success, list of errors on failure
	 */
	static saveEntities(parentEntity, entityList) {

//        def parentEntity        = flow.importer_parentEntity
//        def entityList          = flow.importer_importedData
        def firstEntity         = entityList[0]

        def failedFields        = []

        def application         = ApplicationHolder.application
        def domainClass         = application.getDomainClass(firstEntity.class.name)
        def sessionFactory      = domainClass.validator.sessionFactory
        def findAllMethod       = new FindAllPersistentMethod(sessionFactory, application.classLoader)

        // we need all children of parentEntity of same type as the added
        // entities (including ones to be added)
        def childEntities       = findAllMethod.invoke(
                domainClass.clazz, "findAll",
                ["from ${firstEntity.class.name} as x where x.parent='$parentEntity.id'"] as Object[]) + entityList

        // figure out the collection name via the hasMany property
        def hasMany = GrailsClassUtils.getStaticPropertyValue(parentEntity.class, 'hasMany')
        def collectionName = hasMany.find{it.value == domainClass.clazz}.key.capitalize()

        // add the entities one by one to the parent entity
        entityList.each { parentEntity."addTo$collectionName" it }

        // checks for duplicate subject names because the uniqueness constraint
        // does not work at this point (would cause exception later)
        // see: http://grails.org/doc/latest/ref/Constraints/unique.html
        def checkForDuplicates = { propertyName ->

            def entityProperties = childEntities*."$propertyName"

            def uniques     = [] as Set
            def duplicates  = [] as Set

            // this approach separates the unique from the duplicate entries
            entityProperties.each { uniques.add(it) || duplicates.add(it) }

            if (duplicates) {

                // Collect all entities with a duplicate value of the unique
                // property. Add corresponding entries to 'failedFields'.
                failedFields += entityList.findAll { it."$propertyName" in duplicates }.collect { duplicate ->

                    [   entity :        "entity_${duplicate.getIdentifier()}_$propertyName",
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
            if (hasUniqueConstraint) checkForDuplicates constrainedProperty.key

        }

        if (!failedFields) parentEntity.save(failOnError: true)

        failedFields

	}
    
    /**
    * Method to check if all fields of an entity are empty
    * 
    * @param theEntity entity object
    */    
    def isEntityEmpty(theEntity) {
        def isEmpty = true
        
        // Go through all fields and when a non-null field has been found return false
        theEntity.giveFields().each {            
            if ( (theEntity.getFieldValue(it.name) != null) && (theEntity.getFieldValue(it.name) != 0) ) 
                isEmpty = false
        }  
        
        return isEmpty
    }

    /**
	 * This method reads an Excel row and returns it as filled entity
	 *
     * @param theEntity entity to use
	 * @param theTemplate Template object
	 * @param row POI based Excel row containing Cell objects
	 * @param mcmap map containing MappingColumn objects
	 * @return list of entities and list of failed cells
	 */
    def createEntity(theEntity, theTemplate, Row theRow, mcmap) {
        def df = new DataFormatter()
		def tft = TemplateFieldType
        def error

		// Initialize the entity with the chosen template
		def entity = gdtService.getInstanceByEntityName(theEntity.entity).
            newInstance(template:theTemplate)

		// Read every cell in the Excel row
		for (Cell cell: theRow) {
			
            // Get the MappingColumn information of the current cell
			def mc = mcmap[cell.getColumnIndex()]
			def value

			// Check if column must be imported
			if (mc != null) if (!mc.dontimport) {
				try {					
                    // Format the cell conform the TemplateFieldType
                    value = formatValue(df.formatCellValue(cell), mc.templatefieldtype)                
                } catch (NumberFormatException nfe) {                    
                    // Formatting went wrong, so set the value to an empty string
					value = ""                    
				}

				// Try to set the value for this entity
                try {				
                        entity.setFieldValue(mc.property, value, true)
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
			case TemplateFieldType.STRING: return value.trim()
			case TemplateFieldType.TEXT: return value.trim()
			case TemplateFieldType.LONG: return (long) Double.valueOf(value)			
			case TemplateFieldType.DOUBLE: return Double.valueOf(value.replace(",", "."));
			case TemplateFieldType.STRINGLIST: return value.trim()
			case TemplateFieldType.ONTOLOGYTERM: return value.trim()
			case TemplateFieldType.DATE: return value
			default: return value
		}
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