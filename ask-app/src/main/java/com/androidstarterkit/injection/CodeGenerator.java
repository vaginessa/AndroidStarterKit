package com.androidstarterkit.injection;


import com.androidstarterkit.injection.model.CodeBlock;

import java.util.List;

public interface CodeGenerator {
  void addCodeBlock(CodeBlock codeBlock);
  void addCodeBlocks(List<CodeBlock> codeBlocks);
  List<CodeBlock> getCodeBlocks();
  void apply();
}
