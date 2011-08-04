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

  <g:if test="${failedFields}">
    <h1>Please correct the failed property assignments and make any further adjustments if required</h1>
  </g:if>
  <g:else>
    <h1>Please make any adjustments if required</h1>
  </g:else>

  <g:if test="${showTableEditor == 'on'}">
  <GdtImporter:validation entityList="${importedEntitiesList}" failedFields="${failedFields}"/>
  </g:if>
  <g:else>
    <GdtImporter:previewImportedAndFailedEntities entityList="${importedEntitiesList}" failedFields="${failedFields}"/>
  </g:else>
</af:page>
