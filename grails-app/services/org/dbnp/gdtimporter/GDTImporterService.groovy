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

import org.apache.poi.ss.usermodel.*
import dbnp.studycapturing.*

class GDTImporterService {
    def authenticationService
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
	 * @param sheetindex sheet to use within the workbook
     * @param headerrow row where the header starts
     * @param datamatrix_start row where the actual data starts
     * @param theEntity type of entity we are reading
	 * @return header representation as a MappingColumn hashmap
	 */
    def getHeader(Workbook workbook, int sheetIndex, int headerRow, int datamatrixStart, theEntity = null) {        
    }

    /**
	 * This method is meant to return a matrix of the rows and columns
	 * used in the preview.
	 *
	 * @param workbook Workbook class object
	 * @param sheetIndex sheet index used
     * @param datamatrixStartRow
	 * @param count amount of rows of data to read starting at datamatrixStartRow
	 * @return two dimensional array (matrix) of Cell objects
	 */
    Object[][] getDatamatrixAsCells(Workbook workbook, header, int sheetIndex, int datamatrixStartRow, int count) {
        def sheet = workbook.getSheetAt(sheetIndex)
		def rows = []

		count = (count < sheet.getLastRowNum()) ? count : sheet.getLastRowNum()

		// walk through all rows
		((datamatrixStartRow + sheet.getFirstRowNum())..count).each { rowIndex ->
			def row = []

			(0..header.size() - 1).each { columnIndex ->
				if (sheet.getRow(rowIndex))
					row.add( sheet.getRow(rowIndex).getCell(columnIndex, Row.CREATE_NULL_AS_BLANK) )
			}

			rows.add(row)
		}

		return rows
    }

    /**
	 * Method to read data from a Workbook class object and import entities
     * into a list
	 *
	 * @param template Template to use
	 * @param workbook POI horrible spreadsheet formatted Workbook class object
	 * @param mcmap linked hashmap (preserved order) of MappingColumns
	 * @param sheetIndex sheet to use when using multiple sheets
	 * @param rowIndex first row to start with reading the actual data (NOT the header)
	 * @return list containing entities
	 *
	 * @see org.dbnp.gdtimporter.MappingColumn
	 */
	def getDatamatrixAsEntityList(template, Workbook workbook, int sheetIndex, int rowIndex, mcmap) {
		def sheet = wb.getSheetAt(sheetIndex)		
		def table = []
		def failedcells = [] // list of records

		// walk through all rows and fill the table with records
		(rowIndex..sheet.getLastRowNum()).each { i ->
			// Create an entity record based on a row read from Excel and store the cells which failed to be mapped
			def (record, failed) = createRecord(template, sheet.getRow(i), mcmap)

			// Add record with entity and its values to the table
			table.add(record)

			// If failed cells have been found, add them to the failed cells list
			if (failed?.importcells?.size() > 0) failedcells.add(failed)
		}

		return [table, failedcells]
	}

    /**
	 * Method to store a list containing entities.
	 *
	 * @param study entity Study
     * @param entities list of entities
     * @param authenticationService authentication service
     * @param log log
     *
     * @return 
	 */
	static saveEntities(Study study, entities, authenticationService, log) {
		def validatedSuccesfully = 0
		def entitystored = null

		// Study passed? Sync data
		if (study != null) study.refresh()

			entities.each { entity ->
				switch (entity.getClass()) {
					case Study: log.info ".importer wizard, persisting Study `" + entity + "`: "
						entity.owner = authenticationService.getLoggedInUser()

						if (study.validate()) {
							if (!entity.save(flush:true)) {
								log.error ".importer wizard, study could not be saved: " + entity
								throw new Exception('.importer wizard, study could not be saved: ' + entity)
							}
						} else {
							log.error ".importer wizard, study could not be validated: " + entity
							throw new Exception('.importer wizard, study could not be validated: ' + entity)
						}

						break
					case Subject: log.info ".importer wizard, persisting Subject `" + entity + "`: "
						study.addToSubjects(entity)
						break
					case Event: log.info ".importer wizard, persisting Event `" + entity + "`: "
						study.addToEvents(entity)
						break
					case Sample: log.info ".importer wizard, persisting Sample `" + entity + "`: "
						study.addToSamples(entity)
						break
					case SamplingEvent: log.info ".importer wizard, persisting SamplingEvent `" + entity + "`: "
						study.addToSamplingEvents(entity)
						break
					default: log.info ".importer wizard, skipping persisting of `" + entity.getclass() + "`"
						break
				}
            }

		// validate study
		if (study.validate()) {
			if (!study.save(flush: true)) {
				//this.appendErrors(flow.study, flash.wizardErrors)
				throw new Exception('.importer wizard [saveDatamatrix] error while saving study')
			}
		} else {
			throw new Exception('.importer wizard [saveDatamatrix] study does not validate')
		}

		//return true
        // return useful information
	}


    /**
	 * This method creates a record (array) containing entities with values
	 *
	 * @param template_id template identifier
	 * @param excelrow POI based Excel row containing the cells
	 * @param mcmap map containing MappingColumn objects
	 * @return list of entities and list of failed cells
	 */
    def createRecord(template, Row excelrow, mcmap) {
        
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
