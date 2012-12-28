package com.jeex.sci.test;

public class BlackListQuery {
	private int infoType;
	private String info;
	
	public BlackListQuery(int infoType, String info) {
		this.infoType = infoType;
		this.info = info;
	}
	
	public String getInfo() {
		return info;
	}
	public void setInfo(String info) {
		this.info = info;
	}

	public int getInfoType() {
		return infoType;
	}

	public void setInfoType(int infoType) {
		this.infoType = infoType;
	}
}
