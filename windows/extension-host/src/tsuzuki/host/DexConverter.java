package tsuzuki.host;

import com.googlecode.d2j.Method;
import com.googlecode.d2j.dex.Dex2jar;
import com.googlecode.d2j.dex.DexExceptionHandler;
import com.googlecode.d2j.node.DexClassNode;
import com.googlecode.d2j.node.DexCodeNode;
import com.googlecode.d2j.node.DexFileNode;
import com.googlecode.d2j.node.DexMethodNode;
import com.googlecode.d2j.node.insn.DexStmtNode;
import com.googlecode.d2j.node.insn.MethodStmtNode;
import com.googlecode.d2j.node.insn.Stmt0RNode;
import com.googlecode.d2j.node.insn.TypeStmtNode;
import com.googlecode.d2j.reader.BaseDexFileReader;
import com.googlecode.d2j.reader.MultiDexFileReader;
import com.googlecode.d2j.reader.Op;
import com.googlecode.d2j.visitors.DexFileVisitor;
import com.googlecode.dex2jar.tools.BaksmaliBaseDexExceptionHandler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class DexConverter {
    private static final String OBJECT = "Ljava/lang/Object;";
    private static final int PUBLIC_CONSTRUCTOR = 0x1 | 0x10000;

    private DexConverter() {
    }

    static void convert(Path apk, Path jar) throws IOException {
        BaseDexFileReader reader = new FixingReader(MultiDexFileReader.open(Files.readAllBytes(apk)));
        Path temp = jar.resolveSibling(jar.getFileName() + ".tmp");
        Files.deleteIfExists(temp);
        DexExceptionHandler handler = new BaksmaliBaseDexExceptionHandler();
        Dex2jar.from(reader)
            .withExceptionHandler(handler)
            .reUseReg(false)
            .topoLogicalSort()
            .skipDebug(true)
            .optimizeSynchronized(false)
            .printIR(false)
            .noCode(false)
            .skipExceptions(false)
            .dontSanitizeNames(true)
            .computeFrames(true)
            .to(temp);
        copyResources(apk, temp);
        Files.move(temp, jar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static void copyResources(Path apk, Path jar) throws IOException {
        try (var source = java.nio.file.FileSystems.newFileSystem(apk);
             var target = java.nio.file.FileSystems.newFileSystem(jar, Map.of("create", "false"))) {
            Path root = source.getPath("/");
            try (var walk = Files.walk(root)) {
                for (Path entry : (Iterable<Path>) walk::iterator) {
                    if (Files.isDirectory(entry)) {
                        continue;
                    }
                    String name = root.relativize(entry).toString().replace('\\', '/');
                    if (name.matches("classes\\d*\\.dex") || name.startsWith("META-INF/") || name.equals("AndroidManifest.xml")
                        || name.equals("resources.arsc") || name.endsWith(".class")) {
                        continue;
                    }
                    Path out = target.getPath("/" + name);
                    if (out.getParent() != null) {
                        Files.createDirectories(out.getParent());
                    }
                    Files.copy(entry, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    static int fix(DexFileNode file) {
        Map<String, DexClassNode> byName = new HashMap<>();
        for (DexClassNode node : file.clzs) {
            byName.put(node.className, node);
        }
        int rewritten = 0;
        for (DexClassNode node : List.copyOf(file.clzs)) {
            if (node.methods == null) {
                continue;
            }
            for (DexMethodNode method : List.copyOf(node.methods)) {
                DexCodeNode code = method.codeNode;
                if (code == null || code.stmts == null) {
                    continue;
                }
                boolean inConstructor = "<init>".equals(method.method.getName());
                Map<Integer, String> pending = new HashMap<>();
                for (int i = 0; i < code.stmts.size(); i++) {
                    DexStmtNode stmt = code.stmts.get(i);
                    if (stmt instanceof TypeStmtNode type && stmt.op == Op.NEW_INSTANCE) {
                        pending.put(type.a, type.type);
                        continue;
                    }
                    if (!(stmt instanceof MethodStmtNode call)
                        || (stmt.op != Op.INVOKE_DIRECT && stmt.op != Op.INVOKE_DIRECT_RANGE)
                        || !"<init>".equals(call.method.getName())
                        || call.args.length == 0) {
                        continue;
                    }
                    String owner = call.method.getOwner();
                    String created = pending.remove(call.args[0]);
                    String target;
                    if (created != null) {
                        target = created;
                    } else if (inConstructor) {
                        target = node.superClass;
                    } else {
                        continue;
                    }
                    if (target == null || target.equals(owner) || !isAncestor(byName, target, owner)) {
                        continue;
                    }
                    String[] params = call.method.getParameterTypes();
                    if (!ensureConstructor(byName, target, owner, params)) {
                        continue;
                    }
                    code.stmts.set(i, new MethodStmtNode(stmt.op, call.args, new Method(target, "<init>", params, "V")));
                    rewritten++;
                }
            }
        }
        return rewritten;
    }

    private static boolean isAncestor(Map<String, DexClassNode> byName, String type, String ancestor) {
        String current = type;
        for (int depth = 0; depth < 32 && current != null; depth++) {
            DexClassNode node = byName.get(current);
            if (node == null) {
                return false;
            }
            if (ancestor.equals(node.superClass)) {
                return true;
            }
            current = node.superClass;
        }
        return false;
    }

    private static boolean ensureConstructor(Map<String, DexClassNode> byName, String type, String stopAt, String[] params) {
        if (type.equals(stopAt)) {
            return true;
        }
        DexClassNode node = byName.get(type);
        if (node == null) {
            return false;
        }
        if (hasConstructor(node, params)) {
            return true;
        }
        if (!ensureConstructor(byName, node.superClass, stopAt, params)) {
            return false;
        }
        addConstructor(node, params);
        return true;
    }

    private static boolean hasConstructor(DexClassNode node, String[] params) {
        if (node.methods == null) {
            return false;
        }
        for (DexMethodNode method : node.methods) {
            if ("<init>".equals(method.method.getName()) && java.util.Arrays.equals(method.method.getParameterTypes(), params)) {
                return true;
            }
        }
        return false;
    }

    private static void addConstructor(DexClassNode node, String[] params) {
        if (node.methods == null) {
            node.methods = new ArrayList<>();
        }
        int words = 1;
        for (String param : params) {
            words += param.equals("J") || param.equals("D") ? 2 : 1;
        }
        int[] registers = new int[words];
        for (int r = 0; r < words; r++) {
            registers[r] = r;
        }
        DexMethodNode constructor = new DexMethodNode(PUBLIC_CONSTRUCTOR, new Method(node.className, "<init>", params, "V"));
        DexCodeNode code = new DexCodeNode();
        code.totalRegister = words;
        code.stmts = new ArrayList<>();
        Op invoke = words > 5 ? Op.INVOKE_DIRECT_RANGE : Op.INVOKE_DIRECT;
        code.stmts.add(new MethodStmtNode(invoke, registers, new Method(node.superClass, "<init>", params, "V")));
        code.stmts.add(new Stmt0RNode(Op.RETURN_VOID));
        constructor.codeNode = code;
        node.methods.add(constructor);
    }

    private static final class FixingReader implements BaseDexFileReader {
        private final BaseDexFileReader inner;

        FixingReader(BaseDexFileReader inner) {
            this.inner = inner;
        }

        public int getDexVersion() {
            return inner.getDexVersion();
        }

        public void accept(DexFileVisitor visitor) {
            accept(visitor, 0);
        }

        public List<String> getClassNames() {
            return inner.getClassNames();
        }

        public void accept(DexFileVisitor visitor, int config) {
            DexFileNode node = new DexFileNode();
            inner.accept(node, config);
            fix(node);
            node.accept(visitor);
        }

        public void accept(DexFileVisitor visitor, int classIndex, int config) {
            inner.accept(visitor, classIndex, config);
        }
    }
}
