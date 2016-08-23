package com.androidstarterkit.utils;

public class PrintUtil {
  public static String prefixDash(int depth) {
    String dash;

    if (depth == 0) {
      dash = "├─ ";
    } else {
      dash = "│";
    }

    for (int i = 0; i < depth; i++) {
      dash += "  ";
    }

    if (depth != 0) {
      dash += "└─ ";
    }

    return dash + (depth > 0 ? " " : "");
  }
}
