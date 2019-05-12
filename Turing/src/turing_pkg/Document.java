package turing_pkg;

import java.io.IOException;

public class Document {

	String title;
	int num_sections;
	byte permission;
	
	public Document(String title, int sections, byte permission) throws IOException {
		this.title = title;
		this.num_sections = sections;
		this.permission = permission;
	}
	
}