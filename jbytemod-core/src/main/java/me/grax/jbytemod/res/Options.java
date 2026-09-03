package me.grax.jbytemod.res;

import com.strobel.decompiler.DecompilerSettings;
import de.xbrowniecodez.jbytemod.Main;
import de.xbrowniecodez.jbytemod.utils.Utils;
import de.xbrowniecodez.jbytemod.decompiler.CFRDecompiler;

import me.grax.jbytemod.utils.ErrorDisplay;
import me.grax.jbytemod.res.Option.Type;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;

public class Options {
    private final File propFile = new File(Utils.getWorkingDirectory(), "jbytemod-remastered.cfg");

    public List<Option> bools = new ArrayList<>();
    public List<Option> defaults = new ArrayList<>(Arrays.asList(new Option("sort_methods", false, Type.BOOLEAN),
            new Option("compute_maxs", true, Type.BOOLEAN), new Option("select_code_tab", true, Type.BOOLEAN),
            new Option("memory_warning", true, Type.BOOLEAN), new Option("python_path", "", Type.STRING),
            new Option("hints", false, Type.BOOLEAN, "editor"), new Option("copy_formatted", false, Type.BOOLEAN, "editor"),
            new Option("analyze_errors", true, Type.BOOLEAN, "editor"), new Option("simplify_graph", true, Type.BOOLEAN, "graph"),
            new Option("remove_redundant", false, Type.BOOLEAN, "graph"), new Option("max_redundant_input", 2, Type.INT, "graph"),
            new Option("decompile_graph", true, Type.BOOLEAN, "graph"), new Option("primary_color", "#557799", Type.STRING, "color"),
            new Option("secondary_color", "#995555", Type.STRING, "color"),
            new Option("check_update", true, Type.BOOLEAN), new Option("auto_scan", false, Type.BOOLEAN),
            new Option("bad_class_check", true, Type.BOOLEAN), new Option("use_dark_theme", true, Type.BOOLEAN, "style")));

    public Options() {
        initializeDecompilerOptions();
        if (propFile.exists()) {
            Main.INSTANCE.getLogger().log("Loading settings... ");
            try {
                for (String line : Files.readAllLines(propFile.toPath())) {
                    if (line.isBlank()) {
                        continue;
                    }
                    try {
                        int separator = line.indexOf('=');
                        if (separator < 1) {
                            throw new IllegalArgumentException();
                        }
                        String[] definition = line.substring(0, separator).split(":", 3);
                        if (definition.length != 3) {
                            throw new IllegalArgumentException();
                        }
                        bools.add(new Option(definition[0], line.substring(separator + 1),
                                Type.valueOf(definition[1]), definition[2]));
                    } catch (Exception e) {
                        Main.INSTANCE.getLogger().warn("Couldn't parse line: " + line);
                    }
                }
                if (mergeWithDefaults()) {
                    Main.INSTANCE.getLogger().warn("Option file not matching defaults, updating it...");
                    this.save();
                }
            } catch (IOException e) {
                Main.INSTANCE.getLogger().warn("Couldn't read option file, restoring defaults...");
                this.initWithDefaults(false);
                this.save();
            }
        } else {
             Main.INSTANCE.getLogger().warn("Property File \"" + propFile.getName() + "\" does not exist, creating...");
            this.initWithDefaults(false);
            this.save();
        }
    }

    private boolean mergeWithDefaults() {
        List<Option> loaded = bools;
        List<Option> merged = new ArrayList<>(defaults.size());
        boolean changed = loaded.size() != defaults.size();

        for (Option defaultOption : defaults) {
            Option loadedOption = find(loaded, defaultOption.getName());
            if (loadedOption == null || loadedOption.getType() != defaultOption.getType()) {
                merged.add(defaultOption);
                changed = true;
                continue;
            }
            merged.add(new Option(defaultOption.getName(), loadedOption.getValue(),
                    defaultOption.getType(), defaultOption.getGroup()));
            if (!defaultOption.getName().equals(loadedOption.getName())
                    || !defaultOption.getGroup().equals(loadedOption.getGroup())) {
                changed = true;
            }
        }

        for (Option loadedOption : loaded) {
            if (find(defaults, loadedOption.getName()) == null) {
                changed = true;
                break;
            }
        }
        bools = merged;
        return changed;
    }

    private void initializeDecompilerOptions() {
        for (Entry<String, String> e : CFRDecompiler.options.entrySet()) {
            defaults.add(new Option("cfr_" + e.getKey(), Boolean.valueOf(e.getValue()), Type.BOOLEAN, "decompiler_cfr"));
        }
        try {
            DecompilerSettings s = new DecompilerSettings();
            for (Field f : s.getClass().getDeclaredFields()) {
                if (f.getType() == boolean.class) {
                    f.setAccessible(true);
                    defaults.add(new Option("procyon" + f.getName(), f.getBoolean(s), Type.BOOLEAN, "decompiler_procyon"));
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void initWithDefaults(boolean keepExisting) {
        if (keepExisting) {
            for (Option o : defaults) {
                if (find(o.getName()) == null) {
                    bools.add(o);
                }
            }
            for (Option o : new ArrayList<>(bools)) {
                if (findDefault(o.getName()) == null) {
                    bools.remove(o);
                }
            }
        } else {
            bools = new ArrayList<>();
            bools.addAll(defaults);
        }
    }

    public void save() {
        new Thread(() -> {
            try {
                if (!propFile.exists()) {
                    propFile.getParentFile().mkdirs();
                    propFile.createNewFile();
                     Main.INSTANCE.getLogger().log("Prop file doesn't exist, creating.");
                }
                PrintWriter pw = new PrintWriter(propFile);
                for (Option o : bools) {
                    pw.println(o.getName() + ":" + o.getType().name() + ":" + o.getGroup() + "=" + o.getValue());
                }
                pw.close();
            } catch (Exception e) {
                new ErrorDisplay(e);
            }
        }).start();
    }

    public Option get(String name) {
        Option op = find(name);
        if (op != null) {
            return op;
        }
        JOptionPane.showMessageDialog(null, "Missing option: " + name + "\nRewriting your config file!");
        this.initWithDefaults(false);
        this.save();
        op = find(name);
        if (op != null) {
            return op;
        }
        throw new RuntimeException("Option not found: " + name);
    }

    private Option find(String name) {
        return find(bools, name);
    }

    private static Option find(List<Option> options, String name) {
        for (Option o : options) {
            if (o.getName().equalsIgnoreCase(name)) {
                return o;
            }
        }
        return null;
    }

    private Option findDefault(String name) {
        for (Option o : defaults) {
            if (o.getName().equalsIgnoreCase(name)) {
                return o;
            }
        }
        return null;
    }

}
