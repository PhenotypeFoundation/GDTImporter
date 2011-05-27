/**
 *  GDTImporter, a plugin for to import data into Grails Domain Templates
 *  Copyright (C) 2011 Tjeerd Abma et al
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

class GdtimporterGrailsPlugin {
    // the plugin version
    def version = "0.4.5.1"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.3.6 > *"
    // the other plugins this plugin depends on
    def dependsOn = [gdt:"0.0.44 => *", ajaxflow:"0.2.1 => *"]
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/views/error.gsp"
    ]

    // TODO Fill in these fields
    def author = "Tjeerd Abma"
    def authorEmail = "t[dot]w[dot]abma[at]gmail[dot]com"
    def title = "Importer plugin for Grails Domain Templates (GDT)"
    def description = '''\\
This plugin allows one to import data into Grails Domain Templates (GDT).
'''

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/gdtimporter"
}
