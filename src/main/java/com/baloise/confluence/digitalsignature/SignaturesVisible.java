package com.baloise.confluence.digitalsignature;

public enum SignaturesVisible {
	ALWAYS, IF_SIGNATORY, IF_SIGNED
	;

	public static SignaturesVisible ofValue(String v) {
		try {
			return SignaturesVisible.valueOf(v.toUpperCase().replaceAll("\\W+", "_"));
		} catch (Exception e) {
			return ALWAYS;
		}
		
	}

}
