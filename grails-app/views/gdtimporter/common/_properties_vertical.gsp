<%
	/**
	 * Properties template which shows entities and allows to assign properties to columns
	 *
	 * @author Tjeerd Abma
	 * @since 20100210
	 * @package importer
	 *
	 * Revision information: 
	 * $Rev: 1430 $
	 * $Author: work@osx.eu $
	 * $Date: 2011-01-21 21:05:36 +0100 (Fri, 21 Jan 2011) $
	 */
%>
<g:form name="propertiesform" action="saveProperties">
    <table>
	  <g:each var="stdentity" in ="${standardentities}">	      
	      
	      <% if (selectedentities.any { it.name.toLowerCase() == stdentity.value.name.toLowerCase() } && stdentity.value.name!="") { %>
	      <tr><td colspan="2"><h4>${stdentity.value.name}</h4></td></tr>
	      <tr>		  
		  <td>Columnname:</td>
		  <td>Property:</td>
	      </tr>	      
		<g:each var="selentity" in="${selectedentities}">
		    
		    <g:if test="${selentity.name.toLowerCase()==stdentity.value.name.toLowerCase()}">
			<tr>			    
			    <td class="header" width="200px">
				<b>${header[selentity.columnindex.toInteger()].name}</b>
			    </td>
			    <td>    
				<importer:propertyChooser name="columnproperty" mappingcolumn="${header[selentity.columnindex.toInteger()]}" allfieldtypes="${allfieldtypes}"/>
			    </td>
			</tr>
		    </g:if>
		</g:each>
	      <tr>
		  <td colspan="2">
		      <hr />
		  </td>
	      </tr>
	  <% } %> <!-- end of JSP if-->
	  </g:each>
	<tr>
	    <td>
		<input type="hidden" name="layout" value="${params.layout}">
		<input type="submit" name="savebutton" value="Next"/>
	    </td>
	</tr>

    </table>
</g:form>
