  <%
	/**
	 * Missing properties template which shows missing properties
	 *
	 * @author Tjeerd Abma
	 * @since 20100623
	 * @package importer
	 *
	 * Revision information: 
	 * $Rev: 1492 $
	 * $Author: t.w.abma@umcutrecht.nl $
	 * $Date: 2011-02-04 11:16:53 +0100 (Fri, 04 Feb 2011) $
	 */
%>
<script type="text/javascript">
 $(document).ready(function() {
        // mark error fields
        <g:each in="${importer_failedFields}" var="field">
          var element = $("select[name=${field.entity}]");

          element.addClass('error')
          element.append( new Option("Invalid: ${field.originalValue}","#invalidterm", true, true) );
  
        </g:each>
  });
</script>

<g:if test="${importer_invalidentities}"><br/><br/>
  <b>There are ${importer_invalidentities} entities which could not be validated, they are indicated by a red color, please correct them before continuing.</b>
</g:if>

<div class="wizard" id="wizard">
    <div class="tableEditor">
	<g:set var="showHeader" value="${true}"/>
	    <g:each status="index" var="table" in="${datamatrix}">
		<g:each status="i" var="entity" in="${table}">
		    <g:if test="${showHeader}">
			<g:set var="showHeader" value="${false}"/>
			<div class="header">
				<div class="firstColumn"></div>
				<af:templateColumnHeaders entity="${entity}" class="column" />
			</div>
			<input type="hidden" name="entity" value="${entity.getClass().getName()}">
		    </g:if>
		    <div class="row">
				<div class="firstColumn"></div>
			<af:templateColumns id="${entity.hashCode()}" entity="${entity}" template="${entity.template}" name="entity_${entity.getIdentifier()}" class="column" subject="${entity.hashCode()}" addDummy="true" />
		    </div>
		</g:each>
	    </g:each>
    </div>
    <div class="sliderContainer">
      <div class="slider"></div>
    </div>
    <div>
	<br/>	
    </div>
</div>