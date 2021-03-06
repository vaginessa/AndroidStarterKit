package com.androidstarterkit;

import com.androidstarterkit.android.api.Extension;
import com.androidstarterkit.command.AskJson;
import com.androidstarterkit.command.CommandParser;
import com.androidstarterkit.command.TabType;
import com.androidstarterkit.config.AskConfig;
import com.androidstarterkit.exception.CommandException;
import com.androidstarterkit.file.BuildGradle;
import com.androidstarterkit.file.MainActivity;
import com.androidstarterkit.file.ProguardRules;
import com.androidstarterkit.file.SettingsGradle;
import com.androidstarterkit.directory.RemoteDirectory;
import com.androidstarterkit.directory.SourceDirectory;
import com.androidstarterkit.injection.model.Config;
import com.androidstarterkit.injection.model.LayoutGroup;
import com.androidstarterkit.injection.model.Module;
import com.androidstarterkit.injection.model.ModuleGroup;
import com.androidstarterkit.injection.ModuleLoader;
import com.androidstarterkit.util.FileUtils;
import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Ask {
  public static final int env = AskConfig.DEVELOPMENT;
  public static final int output = AskConfig.OUTPUT_ASK_SAMPLE;

  public static void main(String[] args) {
    CommandParser commandParser;
    try {
      commandParser = new CommandParser(args);
    } catch (CommandException e) {
      Console.log(e);
      return;
    }

    if (commandParser.hasHelpCommand()) {
      Console.printHelp();
      return;
    }

    // initialize CommandParser
    String projectPath = commandParser.getPath();
    final TabType tabType = commandParser.getTabType();
    final List<String> layoutCommands = commandParser.getLayoutCommands();
    final List<String> moduleCommands = commandParser.getModuleCommands();

    if (layoutCommands.size() <= 0) {
      layoutCommands.add("sv");
    }

    /*
      * initialize Modules
      *
      * AskConfig.OUTPUT_PROJECT : output is project
      * AskConfig.OUTPUT_ASK_SAMPLE : output is ask-sample
      */
    String sourceModuleName;
    if (output == AskConfig.OUTPUT_ASK_SAMPLE && projectPath == null) {
      projectPath = FileUtils.getRootPath();
      sourceModuleName = AskConfig.DEFAULT_SAMPLE_MODULE_NAME;
    } else  {
      if (output == AskConfig.OUTPUT_PROJECT && projectPath == null) {
        File sampleDirectory = new File(FileUtils.getRootPath() + "/ask-app/AndroidProject");
        File outputDirectory = new File(FileUtils.getRootPath() + "/output/AndroidProject");

        try {
          FileUtils.copyDirectory(sampleDirectory, outputDirectory);
        } catch (IOException e) {
          e.printStackTrace();
        }

        projectPath = outputDirectory.getPath();
      }

      SettingsGradle settingsGradleFile = new SettingsGradle(projectPath);
      sourceModuleName = settingsGradleFile.getAppModuleName();
    }

    RemoteDirectory remoteDirectory = new RemoteDirectory(FileUtils.getRootPath());
    SourceDirectory sourceDirectory = new SourceDirectory(projectPath, sourceModuleName, remoteDirectory);

    System.out.println("Analyzing Source Project...");
    System.out.println("  package: " + sourceDirectory.getAndroidManifestFile().getPackageName());
    System.out.println("  main activity: " + sourceDirectory.getAndroidManifestFile().getMainActivityName());
    System.out.println("  src path: " + sourceDirectory.getJavaPath());
    System.out.println("  layout path: " + sourceDirectory.getLayoutPath());
    System.out.println();

    // Parsing ask.json
    Gson gson = new Gson();
    AskJson askJson;
    try {
      askJson = gson.fromJson(FileUtils.readFile(FileUtils.linkPathWithSlash(FileUtils.getRootPath(), "ask-app", AskJson.FILE_NAME))
          , AskJson.class);
    } catch (IOException e) {
      throw new CommandException(CommandException.NOT_FOUND_ASK_JSON);
    }
    askJson.replace(FileUtils.getRootPath(), sourceDirectory.getPath()
        , sourceDirectory.getJavaPath(), sourceDirectory.getMainActivityName(), sourceDirectory.getApplicationId());

    // Transform layouts
    List<LayoutGroup> layoutGroups = new ArrayList<>();
    layoutGroups.addAll(layoutCommands.stream()
        .map(askJson::getLayoutGroupByCommand)
        .collect(Collectors.toList()));

    System.out.println("Layout is loading...");
    sourceDirectory.transform(tabType, layoutGroups);

    System.out.println("Module is loading...");
    // Transform modules
    List<Module> modules = new ArrayList<>();
    modules.addAll(moduleCommands.stream()
        .map(askJson::getModuleByCommand)
        .collect(Collectors.toList()));

    BuildGradle projectBuildGradle = sourceDirectory.getProjectBuildGradle();
    BuildGradle appBuildGradleFile = sourceDirectory.getAppBuildGradleFile();
    ProguardRules proguardRules = sourceDirectory.getProguardRules();
    MainActivity mainActivity = sourceDirectory.getMainActivity();

    ModuleLoader moduleLoader = new ModuleLoader();
    moduleLoader.addCodeGenerator(projectBuildGradle);
    moduleLoader.addCodeGenerator(appBuildGradleFile);
    moduleLoader.addCodeGenerator(proguardRules);
    moduleLoader.addCodeGenerator(mainActivity);

    // Add module config
    for (Module module : modules) {
      for (String className : module.getClassNames()) {
        sourceDirectory.transformFileFromRemote(-1, remoteDirectory.getChildFile(className + Extension.JAVA));
      }

      addCodeblockToModuleLoader(module.getConfigs(), sourceDirectory, moduleLoader, projectBuildGradle, appBuildGradleFile, proguardRules, mainActivity);
    }

    // Add group config
    List<Config> groupConfigs = new ArrayList<>();
    for (String command : moduleCommands) {
      ModuleGroup moduleGroup = askJson.getModuleGroupByCommand(command);

      if (!groupConfigs.containsAll(moduleGroup.getGroupConfigs())) {
        groupConfigs.addAll(moduleGroup.getGroupConfigs());
      }
    }
    addCodeblockToModuleLoader(groupConfigs, sourceDirectory, moduleLoader, projectBuildGradle, appBuildGradleFile, proguardRules, mainActivity);

    moduleLoader.generateCode();

    System.out.println();
    System.out.println("  Project path: " + sourceDirectory.getPath());
    System.out.println();

    // Check config file and show warning
    List<ModuleGroup> distinctModuleGroups = new ArrayList<>();
    for (String command : moduleCommands) {
      ModuleGroup moduleGroup = askJson.getModuleGroupByCommand(command);

      if (!distinctModuleGroups.contains(moduleGroup)) {
        distinctModuleGroups.add(moduleGroup);
      }
    }

    for (ModuleGroup moduleGroup : distinctModuleGroups) {
      for (String filename : moduleGroup.getConfigFilenames()) {
        File configFile = new File(sourceDirectory.getPath() + "/" + filename);
        if (!configFile.exists()) {
          System.out.println("  warning: File " + filename + " is missing, download " + moduleGroup.getPage());
        }
      }
    }
  }

  private static void addCodeblockToModuleLoader(List<Config> configs, SourceDirectory sourceDirectory, ModuleLoader moduleLoader, BuildGradle projectBuildGradle, BuildGradle appBuildGradleFile, ProguardRules proguardRules, MainActivity mainActivity) {
    if (configs != null) {
      for (Config config : configs) {
        if (config.getFullPathname().equals(projectBuildGradle.getPath())) {
          projectBuildGradle.addCodeBlocks(config.getCodeBlocks());
        } else if (config.getFullPathname().equals(appBuildGradleFile.getPath())) {
          appBuildGradleFile.addCodeBlocks(config.getCodeBlocks());
        } else if (config.getFullPathname().equals(proguardRules.getPath())) {
          proguardRules.addCodeBlocks(config.getCodeBlocks());
        } else if (config.getFileNameEx().equals(sourceDirectory.getMainActivityExtName())) {
          mainActivity.addCodeBlocks(config.getCodeBlocks());
        }
      }
    }
  }
}
