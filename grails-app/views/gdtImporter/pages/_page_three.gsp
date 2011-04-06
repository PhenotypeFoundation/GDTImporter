<%
/**
 * third wizard page / tab
 *
 * @author Jeroen Wesbeek
 * @since  20101206
 *
 * Revision information:
 * $Rev: 1430 $
 * $Author: work@osx.eu $
 * $Date: 2011-01-21 21:05:36 +0100 (Fri, 21 Jan 2011) $
 */
%>
<af:page>
 <h1>Please fill in the missing mappings</h1>
    <GdtImporter:validation entityList="${gdtImporter_entityList}" failedFields="${gdtImporter_failedFields}"/>
</af:page>

<g:render template="common/error"/>