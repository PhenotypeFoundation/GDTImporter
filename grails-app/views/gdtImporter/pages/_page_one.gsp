<%@ page import="org.dbnp.gdt.GdtService" %>
<%
/**
 * first wizard page / tab
 *
 * @author Jeroen Wesbeek
 * @since  20101206
 *
 * Revision information:
 * $Rev: 1536 $
 * $Author: t.w.abma@umcutrecht.nl $
 * $Date: 2011-02-17 16:52:50 +0100 (Thu, 17 Feb 2011) $
 */
%>
<af:page>
    <title>Importer wizard (simple)</title>
    <h1>Importer wizard</h1>
    <p>You can import your Excel data to the server by choosing a file from your local harddisk in the form below.</p>
	<table border="0">
    	<tr>
	    <td width="100px">
		Choose your Excel file to import:
	    </td>
	    <td width="100px">
		<af:fileFieldElement name="importfile" value="${gdtImporter_params?.importfile}"/>
	    </td>
	</tr>
	<tr>
	    <td width="100px">
		Use data from sheet:
	    </td>
	    <td width="100px">
		<g:select name="sheetindex" from="${1..25}" value="${gdtImporter_params?.sheetindex}"/>
	    </td>
	</tr>
	<tr>
	    <td width="100px">
		Columnheader starts at row:
	    </td>
	    <td width="100px">
		<g:select name="headerrow" from="${1..10}" value="${gdtImporter_params?.headerrow} optionKey="${{it-1}}"/>
	    </td>
	</tr>
	<tr>
	    <td width="100px">
		Data starts at row:
	    </td>
	    <td width="100px">
		<g:select name="dataMatrix_start" from="${2..10}" value="${gdtImporter_params?.dataMatrix_start}"/>
	    </td>
	</tr>
	<tr>
	    <td>
		Choose type of data:
	    </td>
	    <td>
		<g:select
		name="entity"
		id="entity"
		from="${GdtService.cachedEntities}"
        value="${gdtImporter_params?.entity}"
		optionValue="${{it.name}}"
		optionKey="${{it.encoded}}"
		noSelection="['':'-Choose type of data-']"
		onChange="${remoteFunction( controller: 'importer',
					    action:'ajaxGetTemplatesByEntity',
					    params: '\'entity=\'+escape(this.value)',
					    onSuccess:'updateSelect(\'template_id\',data,false,false,\'default\')')}" />
	    </td>
	</tr>
    <tr id="parentEntityField">
	    <td>
		Choose your ${gdtImporter_parentEntityClassName.toLowerCase()}:
	    </td>
	    <td>
		<g:select name="parentEntity.id" from="${gdtImporter_userParentEntities}" optionKey="id" />
		%{--<g:select name="parentEntity.id" from="${gdtImporter_userParentEntities}" optionKey="id" optionValue="${ it.code + ' - ' + it.title }"/>--}%
	    </td>
	</tr>
	<tr>
	    <td>
		<div id="datatemplate">Choose type of data template:</div>
	    </td>
	    <td>
		<g:select rel="template" entity="none" name="template_id" optionKey="id" optionValue="name" from="${gdtImporter_datatemplates}" value="${gdtImporter_params?.template_id}"/>
	    </td>
	</tr>
	</table>

    <div id="datamatrixpreview"></div>


</af:page>

<g:render template="common/error"/>