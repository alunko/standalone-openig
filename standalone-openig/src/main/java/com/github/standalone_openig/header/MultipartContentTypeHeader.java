package com.github.standalone_openig.header;

import java.util.List;

import org.forgerock.openig.header.ContentTypeHeader;
import org.forgerock.openig.header.HeaderUtil;
import org.forgerock.openig.http.Message;

/**
 * Supported Multipart ContentTypeHeader.
 * see https://bugster.forgerock.org/jira/browse/OPENIG-4
 *
 */
public class MultipartContentTypeHeader extends ContentTypeHeader{
	
	
	
    public MultipartContentTypeHeader() {
		super();
	}

	public MultipartContentTypeHeader(Message message) {
		super(message);
	}

	public MultipartContentTypeHeader(String string) {
		super(string);
	}

	@Override
    public void fromString(String string) {
        clear();
        List<String> parts = HeaderUtil.split(string, ';');
        if (parts.size() > 0) {
            this.type = parts.get(0);
            this.charset = HeaderUtil.parseParameters(parts).get("charset");
            if (this.type.equals("multipart/form-data")) { 
            	this.type = string;
            }
        }
    }
}
