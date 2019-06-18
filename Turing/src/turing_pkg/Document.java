package turing_pkg;

public class Document {

	private String title;
	private String owner;
	private byte[] section_status;
	private int num_sections;
	private byte permission;
	
	public Document(String title, String owner, int sections, byte permission) {
		this.title = title;
		this.owner = owner;
		this.num_sections = sections; 
		this.permission = permission;
		section_status = new byte[sections];
		for (int i=0; i<sections; i++) { 
			section_status[i] = Config.FREE_SECTION;
		}
	}
	
	public String getTitle() {
		return this.title;
	}

	public String getOwner() {
		return this.owner;
	}
	
	public byte getPermission() {
		return this.permission;
	}
	
	public int getSectionCount() {
		return this.num_sections;
	}
	
	public byte getStatus(int section) {
		return this.section_status[section];
	}
	
	public void setStatus(byte status, int section) {
		this.section_status[section] = status;
	}
}