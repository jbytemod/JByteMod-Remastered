package de.xbrowniecodez.jbytemod.decompiler;

import de.xbrowniecodez.jbytemod.Main;
import de.xbrowniecodez.jbytemod.JByteMod;
import me.grax.jbytemod.decompiler.Decompiler;
import me.grax.jbytemod.ui.DecompilerPanel;
import org.benf.cfr.reader.PluginRunner;
import org.benf.cfr.reader.apiunreleased.ClassFileSource2;
import org.benf.cfr.reader.apiunreleased.JarContent;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.state.ClassFileSourceImpl;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.state.TypeUsageCollectingDumper;
import org.benf.cfr.reader.util.AnalysisType;
import org.benf.cfr.reader.util.bytestream.BaseByteData;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.ToStringDumper;
import org.objectweb.asm.tree.MethodNode;

import java.io.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class CFRDecompiler extends Decompiler {

    public static final HashMap<String, String> options = new HashMap<>();

    static {
        options.put("aexagg", "false");
        options.put("allowcorrecting", "true");
        options.put("arrayiter", "true");
        options.put("caseinsensitivefs", "false");
        options.put("clobber", "false");
        options.put("collectioniter", "true");
        options.put("commentmonitors", "false");
        options.put("decodeenumswitch", "true");
        options.put("decodefinally", "true");
        options.put("decodelambdas", "true");
        options.put("decodestringswitch", "true");
        options.put("dumpclasspath", "false");
        options.put("eclipse", "true");
        options.put("elidescala", "false");
        options.put("forcecondpropagate", "false");
        options.put("forceexceptionprune", "false");
        options.put("forcereturningifs", "false");
        options.put("forcetopsort", "false");
        options.put("forcetopsortaggress", "false");
        options.put("forloopaggcapture", "false");
        options.put("hidebridgemethods", "true");
        options.put("hidelangimports", "true");
        options.put("hidelongstrings", "false");
        options.put("hideutf", "true");
        options.put("innerclasses", "true");
        options.put("j14classobj", "false");
        options.put("labelledblocks", "true");
        options.put("lenient", "false");
        options.put("liftconstructorinit", "true");
        options.put("override", "true");
        options.put("pullcodecase", "false");
        options.put("recover", "true");
        options.put("recovertypeclash", "false");
        options.put("recovertypehints", "false");
        options.put("relinkconststring", "true");
        options.put("removebadgenerics", "true");
        options.put("removeboilerplate", "true");
        options.put("removedeadmethods", "true");
        options.put("removeinnerclasssynthetics", "true");
        options.put("rename", "false");
        options.put("renamedupmembers", "false");
        options.put("renameenumidents", "false");
        options.put("renameillegalidents", "false");
        options.put("showinferrable", "false");
        options.put("silent", "false");
        options.put("stringbuffer", "false");
        options.put("stringbuilder", "true");
        options.put("sugarasserts", "true");
        options.put("sugarboxing", "true");
        options.put("sugarenums", "true");
        options.put("tidymonitors", "true");
        options.put("usenametable", "true");
    }

    public CFRDecompiler(JByteMod jbm, DecompilerPanel dp) {
        super(jbm, dp);
    }

    private static DCCommonState initDCState(Map<String, String> optionsMap, ClassFileSource2 classFileSource) {
        OptionsImpl options = new OptionsImpl(optionsMap);
        if (classFileSource == null) classFileSource = new ClassFileSourceImpl(options);
        DCCommonState dcCommonState = new DCCommonState(options, classFileSource);
        return dcCommonState;
    }

    public String decompile(byte[] b, MethodNode mn) {
        try {
            HashMap<String, String> ops = new HashMap<>();
            ops.put("comments", "false");
            for (String key : options.keySet()) {
                ops.put(key, String.valueOf(Main.INSTANCE.getJByteMod().getOptions().get("cfr_" + key).getBoolean()));
            }
            ClassFileSource2 cfs = new ClassFileSource2() {

                public JarContent addJarContent(String s) {
                    return null;
                }

                @Override
                public void informAnalysisRelativePathDetail(String a, String b) {
                }

                @Override
                public String getPossiblyRenamedPath(String path) {
                    return path;
                }

                @Override
                public Pair<byte[], String> getClassFileContent(String path) throws IOException {
                    String name = path.substring(0, path.length() - 6);
                    if (name.equals(cn.name)) {
                        return Pair.make(b, name);
                    }
                    return null; //cfr loads unnecessary classes
                }

                @Override
                public Collection<String> addJar(String arg0) {
                    throw new RuntimeException();
                }

				@Override
				public JarContent addJarContent(String jarPath, AnalysisType analysisType) {
					// TODO Auto-generated method stub
					return null;
				}
            };
            PluginRunner runner = new PluginRunner(ops, cfs);
            if (mn != null) {
                BaseByteData data = new BaseByteData(b);
                DCCommonState state = initDCState(ops, cfs);
                ClassFile cf = new ClassFile(data, "", state);
                state.configureWith(cf);
                cf.analyseTop(state, new TypeUsageCollectingDumper(state.getOptions(), cf));
                for (Method m : cf.getMethodByName(mn.name)) {
                    if (m.getMethodPrototype().getOriginalDescriptor().equals(mn.desc)) {
                        ToStringDumper tsd = new ToStringDumper();
                        m.dump(tsd, true);
                        return tsd.toString();
                    }
                }
            }
            return runner.getDecompilationFor(cn.name);
        } catch (Exception e) {
            e.printStackTrace();
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            return sw.toString();
        }
    }

    protected Pair<byte[], String> getSystemClass(String name, String path) throws IOException {
        InputStream is = ClassLoader.getSystemClassLoader().getResourceAsStream(path);
        if (is != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = is.read(buffer)) > 0) {
                baos.write(buffer, 0, n);
            }
            return Pair.make(baos.toByteArray(), name);
        }
        return null;
    }
}
