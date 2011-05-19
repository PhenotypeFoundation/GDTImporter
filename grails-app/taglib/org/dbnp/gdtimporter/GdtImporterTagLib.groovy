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

/**
 * The GdtImporter tag library contains easy tags for displaying and
 * working with imported data
 */

class GdtImporterTagLib {
    static namespace = 'GdtImporter'
	def GdtImporterService
	def GdtService

    /**
	 * @param header string array containing header
	 * @param datamatrix two dimensional array containing actual data
	 * @return preview of the data with the ability to adjust the datatypes
	 */
	def preview = { attrs ->

		def header = attrs['header']
		def dataMatrix = attrs['dataMatrix']

		out << render(template: "common/preview", plugin: "gdtimporter", model: [header: header, datamatrix: dataMatrix])
	}

	def entity = { attrs ->
		out << entities[attrs['index']].name
	}

	def datapreview = { attrs ->
		def dataMatrix = attrs['dataMatrix']
		out << render(template: "common/datapreview", plugin: "gdtimporter", model: [dataMatrix: dataMatrix])
	}

	/**
	 * Show missing properties
	 */
	def validation = { attrs ->
		def entityList = attrs['entityList']
		def failedFields = attrs['failedFields']
		out << render(template: "common/validation", plugin: "gdtimporter", model: [entityList: entityList, failedFields: failedFields])
	}

	/**	 
	 * @param header array containing mappingcolumn objects	 
	 */
	def properties = { attrs ->
		def header = attrs['header']

		out << render(template: "common/properties", plugin: "gdtimporter", model: [header:header])
	}

	/**
	 * Possibly this will later on return an AJAX-like autocompletion chooser for the fields?
	 *
	 * @param name name for the property chooser element
	 * @param importtemplate_id template identifier where fields are retrieved from
	 * @param matchvalue value which will be looked up via fuzzy matching against the list of options and will be selected
     * @param selected value to be selected by default
	 * @param MappingColumn object containing all required information
     * @param fuzzymatching boolean true if fuzzy matching should be used, otherwise false
	 * @param allfieldtypes boolean true if all templatefields should be listed, otherwise only show filtered templatefields
     * @param extraOptions options to add to the select boxes (e.g. non template field items)
	 * @return chooser object
	 * */
	def propertyChooser = { attrs ->
		// TODO: this should be changed to retrieving fields per entity instead of from one template
		//	 and session variables should not be used inside the service, migrate to controller

 		def t = Template.get(attrs['template_id'])
		def mc = attrs['mappingcolumn']
        def matchvalue = (attrs['fuzzymatching']=="true") ? attrs['matchvalue'] : ""
        def selected = (attrs['selected']) ? attrs['selected'] : ""
		def fuzzyTreshold = attrs[ 'treshold' ] && attrs[ 'treshold' ].toString().isNumber() ? Float.valueOf( attrs[ 'treshold' ] ) : 0.1;
        def returnmatchonly = attrs['returnmatchonly']

        def templatefields = t.fields + mc.entityclass.giveDomainFields() + (attrs.extraOptions ?: [])

        //  Just return the matched value only
        if (returnmatchonly)
            out << GdtImporterService.mostSimilar(matchvalue, templatefields, fuzzyTreshold)
        else // Return a selectbox
            out << createPropertySelect(attrs['name'], templatefields, matchvalue, selected, mc.index, fuzzyTreshold)

	}

	/**
	 * Create the property chooser select element
	 *
	 * @param name name of the HTML select object
	 * @param options list of options (fields) to be used
	 * @param matchvalue value which will be looked up via fuzzy matching against the list of options and will be selected
	 * @param columnIndex column identifier (corresponding to position in header of the Excel sheet)
	 * @return HTML select object
	 */
	def createPropertySelect(String name, options, matchvalue, selected, Integer columnIndex, float fuzzyTreshold = 0.1f) {
		// Determine which field in the options list matches the best with the matchvalue
		def mostsimilar = (matchvalue) ? GdtImporterService.mostSimilar(matchvalue, options, fuzzyTreshold) : ""

		def res = "<select style=\"font-size:10px\" id=\"${name}.index.${columnIndex}\" name=\"${name}.index.${columnIndex}\">"
        def prefIdentifier = options.find { it.preferredIdentifier}

        res += "<option value=\"dontimport\">Don't import</option>"
        res += '<option value="' + prefIdentifier + '">' + prefIdentifier.name + " (IDENTIFIER)</option>"

        options.findAll {!it.preferredIdentifier}.each { f ->
			res +=  "<option value=\"${f.name}\""

			// mostsimilar string passed as argument or selected value passed?
            res += (mostsimilar.toString().toLowerCase() == f.name.toLowerCase() || selected.toLowerCase() == f.name.toLowerCase() ) ?
				" selected='selected'>" :
				">"

			res += """${f.name} ${(!f.unit)?'': '(' + f.unit + ')'}</option>"""
		}

        res += "</select>"
		return res
	}
}
