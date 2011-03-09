<%
/**
 * last wizard page / tab
 *
 * @author Jeroen Wesbeek
 * @since  20101206
 *
 * Revision information:
 * $Rev: 1469 $
 * $Author: t.w.abma@umcutrecht.nl $
 * $Date: 2011-02-01 17:33:41 +0100 (Tue, 01 Feb 2011) $
 */
%>
<script type="text/javascript">
                // disable redirect warning
                var warnOnRedirect = false;
</script>

<af:page>
<h1>Final Page</h1>
<p>
This concludes the importer wizard. You can click <g:link action="index">here</g:link> to restart the wizard.
</p>

All rows were imported succesfully.

    <g:if test="${importer_referer}">
      <p>Click <a href="${importer_referer}">here</a> to return to the page you came from.</p>
    </g:if>
</af:page>
