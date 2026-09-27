package us.dingl.VowSMPPlugin.Gui;

public enum SlotPermission {
    NONE,          // display only, all interaction cancelled
    EXTRACT_ONLY,  // can take items out, can't put in
    INSERT_ONLY,   // can put items in, can't take out
    FULL           // normal chest-like behavior
}