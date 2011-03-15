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

class GdtImporterService {
    def authenticationService
    def GdtService
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
	 * @param template Template to use
	 * @param workbook POI horrible spreadsheet formatted Workbook class object
	 * @param mcmap linked hashmap (preserved order) of GDTMappingColumns
	 * @param sheetIndex sheet to use when using multiple sheets
	 * @param datamatrixRowIndex first row to start with reading the actual data (NOT the header)
	 * @return list containing entities
	 *
	 * @see org.dbnp.gdtimporter.GDTMappingColumn
	 */
	def getDatamatrixAsEntityList(theEntity, theTemplate, Workbook workbook, int sheetIndex, int datamatrixRowIndex, mcmap) {
		def sheet = wb.getSheetAt(sheetIndex)		
		def entityList = []
		def errorList = []

		// Walk through all rows and fill the table with entities
		(datamatrixRowIndex..sheet.getLastRowNum()).each { i ->
			
            // Create an entity record based on a row read from Excel and store the cells which failed to be mapped
			def (entity, error) = createEntity(theEntity, theTemplate, sheet.getRow(i), mcmap)

			// Add entity to the table
			entityList.add(entity)

			// If failed cells have been found, add them to the error list
            // Error contains the entity+identifier+property and the original (failed) value
			if (errorList) errorList.add(error)
		}

		[entityList, errorList]
	}

    /**
	 * Method to store a list containing entities.
     * TODO: change to a generic way, something like addToEntity?
	 *
	 * @param parentEntity parent entity (Study) to add entities to
     * @param entities list of entities
     * @param authenticationService authentication service
     * @param log log
     *
     * @return 
	 */
	static saveEntities(parentEntity, entityList, authenticationService, log) {

		// parentEntiy (Study) passed? Sync data
		if (parentEntity != null) parentEntity.refresh()

			entityList.each { entity ->
				switch (entity.getClass()) {
					case Study: log.info ".importer wizard, persisting Study `" + entity + "`: "
					
						// Validate the study and try to save it
                        if (entity.validate()) {
							if (!entity.save(flush:true)) {
								log.error ".importer wizard, study could not be saved: " + entity
								throw new Exception('.importer wizard, study could not be saved: ' + entity)
							}
						} else {
							log.error ".importer wizard, study could not be validated: " + entity
							throw new Exception('.importer wizard, study could not be validated: ' + entity)
						}

						break
					case Subject: 
                        log.info ".importer wizard, persisting Subject `" + entity + "`: "
						parentEntity.addToSubjects(entity)
						break
					case Event:
                        log.info ".importer wizard, persisting Event `" + entity + "`: "
						parentEntity.addToEvents(entity)
						break
					case Sample:
                        log.info ".importer wizard, persisting Sample `" + entity + "`: "
						parentEntity.addToSamples(entity)
						break
					case SamplingEvent:
                        log.info ".importer wizard, persisting SamplingEvent `" + entity + "`: "
						parentEntity.addToSamplingEvents(entity)
						break
					default: log.info ".importer wizard, skipping persisting of `" + entity.getclass() + "`"
						break
				}
            }

		// All entities have been added to the study, now validate and store the study
		if (parentEntity.validate()) {
			if (!parentEntity.save(flush: true)) {
				//this.appendErrors(flow.study, flash.wizardErrors)
				throw new Exception('.importer wizard [saveEntities] error while saving Study')
			}
		} else {
			throw new Exception('.importer wizard [saveEntities] Study does not validate')
		}
        
        // If there was no validation or save exception, this function returns true
        true
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

		// Initialize the entity with the chosen template
		def entity = GdtService.getInstanceByEntity(theEntity)
        entity.template = theTemplate

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
                        entity.setFieldValue(mc.property, value)					
				} catch (Exception iae) {
					
                    // The entity field value could not be set
                    log.error ".import wizard error could not set property `" + mc.property + "` to value `" + value + "`"					
                    
					// Store the error value (might improve this with name of entity instead of "entity_")
                    // as a map containing the entity+identifier+property and the original value which failed
                    def error = [ entity: "entity_" + entity.getIdentifier() + "_" + mc.property, originalValue: value]
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