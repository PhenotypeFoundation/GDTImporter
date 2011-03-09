<%
	/**
	 * Postview template which will display how the data was imported
	 *
	 * @author Tjeerd Abma
	 * @since 20100317
	 * @package importer
	 *
	 * Revision information: 
	 * $Rev: 1430 $
	 * $Author: work@osx.eu $
	 * $Date: 2011-01-21 21:05:36 +0100 (Fri, 21 Jan 2011) $
	 */
%>
    <table>	
	<tr>	  
	    <g:each var="table" in="${datamatrix}">
		<g:each var="entity" in="${table}">
		    <tr>
			<g:each var="field" in="${entity.giveFields()}">
			    <g:if test="${entity.getFieldValue(field.name)!=null}">
			    <td class="header">
				<b>${field.name}</b>
				${entity.getFieldValue(field.name)}				
			    </td>
			    </g:if>
			    <g:else>
				<td class="valueundefined" >
				    <b>${field.name}</b>
				    &#215;
				</td>
			    </g:else>
			</g:each>
		    </tr>
		</g:each>
	    </g:each>
	</tr>
    </table> 
