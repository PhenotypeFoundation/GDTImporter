<%
	/**
	 * Data preview template which will display cells and columns from a matrix datasource
	 *
	 * @author Tjeerd Abma
	 * @since 20100622
	 * @package importer
	 *
	 * Revision information: 
	 * $Rev: 1430 $
	 * $Author: work@osx.eu $
	 * $Date: 2011-01-21 21:05:36 +0100 (Fri, 21 Jan 2011) $
	 */
%>
<table width="100%">
<g:each var="row" in="${datamatrix}">
	    <tr>		
		<g:each var="cell" in="${row}">
		    <td class="datamatrix">
			<g:if test="${cell.toString()==''}">.</g:if>
			<g:else><gdtimporter:displayCell cell="${cell}"/></g:else>
		    </td>
		</g:each>
	    </tr>
</g:each>
</table>