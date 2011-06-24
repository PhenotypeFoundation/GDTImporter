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

  <g:if test="${gdtImporter_failedFields}">
    <h1>Please correct the failed property assignments and make any further adjustments if required</h1>
  </g:if>
  <g:else>
    <h1>Please make any adjustments if required</h1>
  </g:else>

  <GdtImporter:validation entityList="${importedEntitiesList}" failedFields="${gdtImporter_failedFields}"/>
</af:page>
