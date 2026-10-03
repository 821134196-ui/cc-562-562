package com.cupcheck.model;

public enum HygieneResult {
    /** 杯口清洁，通过 */
    CLEAN,
    /** 杯口不洁 */
    DIRTY_RIM,
    /** 杯内有残留物 */
    RESIDUE,
    /** 杯体破损 */
    DAMAGED
}
