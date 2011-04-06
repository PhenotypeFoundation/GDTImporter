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
 <h1>Confirmation</h1>
  The import was successful. You are about to import ${gdtImporter_entityList.size()}
  <g:if test="${gdtImporter_entityList.size() == 1}">entity.</g:if>
  <g:else >entities.</g:else>

  <g:if test="${gdtImporter_numberOfUpdatedEntities}" >
    Of those entities, ${gdtImporter_numberOfUpdatedEntities} already
    <g:if test="${gdtImporter_numberOfUpdatedEntities == 1}" >
      exists
    </g:if>
    <g:else>
      exist
    </g:else>
   in the database according to their preferred identifier and will be updated.
  </g:if>
  If this is correct, please click 'next' to continue.


</af:page>

<g:render template="common/error"/>