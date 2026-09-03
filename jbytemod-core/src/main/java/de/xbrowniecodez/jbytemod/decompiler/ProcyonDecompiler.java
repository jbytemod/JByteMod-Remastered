package de.xbrowniecodez.jbytemod.decompiler;

import com.strobel.assembler.InputTypeLoader;
import com.strobel.assembler.metadata.*;
import com.strobel.decompiler.DecompilationOptions;
import com.strobel.decompiler.DecompilerSettings;
import com.strobel.decompiler.PlainTextOutput;
import com.strobel.decompiler.languages.java.JavaLanguage;
import com.strobel.decompiler.languages.java.ast.AstNode;
import com.strobel.decompiler.languages.java.ast.CompilationUnit;
import com.strobel.decompiler.languages.java.ast.EntityDeclaration;
import com.strobel.decompiler.languages.java.ast.Keys;
import de.xbrowniecodez.jbytemod.Main;
import de.xbrowniecodez.jbytemod.JByteMod;
import me.grax.jbytemod.decompiler.Decompiler;
import me.grax.jbytemod.ui.DecompilerPanel;
import org.objectweb.asm.tree.MethodNode;

import java.io.StringWriter;
import java.lang.reflect.Field;

public class ProcyonDecompiler extends Decompiler {

    public ProcyonDecompiler(JByteMod jbm, DecompilerPanel dp) {
        super(jbm, dp);
    }

    public String decompile(byte[] b, MethodNode mn) {
        try {
            DecompilerSettings settings = createDecompilerSettings();
            settings.setShowSyntheticMembers(true);

            MetadataSystem metadataSystem = createMetadataSystem(b);
            TypeReference type = metadataSystem.lookupType(cn.name);
            DecompilationOptions decompilationOptions = createDecompilationOptions(settings);

            TypeDefinition resolvedType = resolveType(type);
            if (resolvedType == null) {
                return "Unable to resolve type.";
            }

            StringWriter stringWriter = new StringWriter();
            PlainTextOutput output = new PlainTextOutput(stringWriter);
            if (mn == null) {
                settings.getLanguage().decompileType(resolvedType, output, decompilationOptions);
            } else {
                return decompileMethod(settings, resolvedType, decompilationOptions, mn);
            }
            return stringWriter.toString();
        } catch (Exception e) {
            return e.getStackTrace().toString();
        }
    }

    private DecompilerSettings createDecompilerSettings() throws IllegalAccessException {
        DecompilerSettings settings = new DecompilerSettings();
        for (Field f : settings.getClass().getDeclaredFields()) {
            if (f.getType() == boolean.class && f.getName().startsWith("procyon")) {
                f.setAccessible(true);
                f.setBoolean(settings, Main.INSTANCE.getJByteMod().getOptions().get(f.getName()).getBoolean());
            }
        }
        return settings;
    }

    private MetadataSystem createMetadataSystem(byte[] b) {
        return new MetadataSystem(new ITypeLoader() {
            private InputTypeLoader backLoader = new InputTypeLoader();

            @Override
            public boolean tryLoadType(String s, Buffer buffer) {
                if (s.equals(cn.name)) {
                    buffer.putByteArray(b, 0, b.length);
                    buffer.position(0);
                    return true;
                } else {
                    return backLoader.tryLoadType(s, buffer);
                }
            }
        });
    }

    private String decompileMethod(DecompilerSettings settings, TypeDefinition type,
                                   DecompilationOptions options, MethodNode selectedMethod) {
        if (!(settings.getLanguage() instanceof JavaLanguage language)) {
            return "Method-only decompilation is only supported for Java output.";
        }
        CompilationUnit compilationUnit = language.decompileTypeToAst(type, options);
        for (AstNode node : compilationUnit.getDescendantsAndSelf()) {
            if (!(node instanceof EntityDeclaration declaration)) {
                continue;
            }
            MethodDefinition method = declaration.getUserData(Keys.METHOD_DEFINITION);
            if (method != null && method.getName().equals(selectedMethod.name)
                    && method.getErasedSignature().equals(selectedMethod.desc)) {
                return declaration.getText(settings.getJavaFormattingOptions());
            }
        }
        return "Unable to resolve method " + selectedMethod.name + selectedMethod.desc + ".";
    }

    private DecompilationOptions createDecompilationOptions(DecompilerSettings settings) {
        DecompilationOptions decompilationOptions = new DecompilationOptions();
        decompilationOptions.setSettings(settings);
        decompilationOptions.setFullDecompilation(true);
        return decompilationOptions;
    }

    private TypeDefinition resolveType(TypeReference type) {
        if (type == null) {
            return null;
        }
        return type.resolve();
    }

}
