package com.androidstarterkit.injection;


import java.util.ArrayList;
import java.util.List;

public class ModuleLoader {

  private List<CodeGenerator> codeGenerators;

  public ModuleLoader() {
    codeGenerators = new ArrayList<>();
  }

  public void generateCode() {
    for (CodeGenerator generator : codeGenerators) {
      generator.apply();
    }
  }

  public void addCodeGenerator(CodeGenerator codeGenerator) {
    if (!codeGenerators.contains(codeGenerator)) {
      codeGenerators.add(codeGenerator);
    }
  }
}
