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
    <input type="hidden" id="pageOneRefresh" name="pageOneRefresh" value="${gdtImporter_params?.pageOneRefresh}"/>
	<table border="0">
        <colgroup width="30%">
    	<tr>
	    <td width="100px">
		Choose your Excel file to import:
	    </td>
	    <td width="100px">
		<af:fileFieldElement name="importfile" value="${gdtImporter_params?.importfile}" id="importfile"/>
	    </td>
	</tr>
    <tr>
	    <td width="100px">
		Date format:
	    </td>
	    <td width="100px">
		<g:select name="dateformat" value="${gdtImporter_params?.dateformat}" from="${['dd/MM/yyyy (EU/India/South America/North Africa/Asia/Australia)', 'yyyy/MM/dd (China/Korea/Iran/Japan)', 'MM/dd/yyyy (US)']}" keys="${['dd/MM/yyyy','yyyy/MM/dd','MM/dd/yyyy']}"/>
	    </td>
	</tr>
	<tr>
	    <td width="100px">
		Use data from sheet:
	    </td>
	    <td width="100px">
		<g:select name="sheetIndex" value="${gdtImporter_params?.sheetIndex}" from="${gdtImporter_sheets}" onchange="updateDatamatrixPreview()"/>
	    </td>
	</tr>
	<tr>
	    <td width="100px">
		Column header is at:
	    </td>
	    <td width="100px">
		<g:select name="headerRowIndex" from="${1..10}" value="${gdtImporter_params?.headerRowIndex} optionKey="${{it-1}}"/>
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
		onChange="${remoteFunction( controller: 'gdtImporter',
					    action:'ajaxGetTemplatesByEntity',
					    params: '\'entity=\'+escape(this.value)',
					    onSuccess:'updateSelect(\'template_id\',data,false,false,\'default\',\''+ gdtImporter_parentEntityClassName +'\')')}"/>
      </td>
    </tr>
    <tr id="parentEntityField">
      <td>
        Choose your ${gdtImporter_parentEntityClassName.toLowerCase()}:
      </td>
      <td>
        <g:select name="parentEntity.id" from="${gdtImporter_userParentEntities}" optionKey="id"/>
        %{--<g:select name="parentEntity.id" from="${gdtImporter_userParentEntities}" optionKey="id" optionValue="${ it.code + ' - ' + it.title }"/>--}%
      </td>
    </tr>
    <tr>
      <td>
        <div id="datatemplate">Choose type of data template:</div>
      </td>
      <td>  <g:if test="${gdtImporter_params?.entity}">
                 <g:set var="entity" value="${gdtImporter_params?.entity}"/>
            </g:if>
            <g:else>
                <g:set var="entity" value="None"/>
            </g:else>

        <g:select rel="template" entity="${entity}" name="template_id" optionKey="id" optionValue="name" from="${gdtImporter_datatemplates}" value="${gdtImporter_params?.template_id}"/>
      </td>
    </tr>
  </table>

    <div id="datamatrixpreview"></div>

</af:page>
