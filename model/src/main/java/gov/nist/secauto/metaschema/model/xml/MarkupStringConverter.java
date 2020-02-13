package gov.nist.secauto.metaschema.model.xml;

import java.io.IOException;
import java.io.StringWriter;

import org.apache.xmlbeans.XmlOptions;

import gov.nist.itl.metaschema.model.xml.MarkupContentType;
import gov.nist.secauto.metaschema.datatype.markup.MarkupMultiline;

public class MarkupStringConverter {
	private MarkupStringConverter() {
		// disable construction
	}

	public static MarkupMultiline toMarkupString(MarkupContentType content) {
		XmlOptions options = new XmlOptions();
		options.setSaveInner();
		options.setSaveUseOpenFrag();
		StringWriter writer = new StringWriter();
		try {
			content.save(writer, options);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		String retval = writer.toString().replaceFirst("^<frag\\:fragment[^>]+>", "").replaceFirst("</frag\\:fragment>$", "");
		return MarkupMultiline.fromHtml(retval);
	}
}