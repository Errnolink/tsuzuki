package tsuzuki.host;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Main {
    private static final Object OUT = new Object();
    private static Writer out;

    public static void main(String[] args) throws Exception {
        PrintStream protocol = System.out;
        System.setOut(System.err);
        out = new OutputStreamWriter(protocol, StandardCharsets.UTF_8);
        String root = args.length > 0 ? args[0] : "host-data";
        Runtime.boot(root);
        Extensions extensions = new Extensions(root);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        reply(JSON.toJSONString(new JSONObject().fluentPut("id", 0).fluentPut("ready", true)));
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            JSONObject request = JSON.parseObject(line);
            pool.submit(() -> handle(extensions, request));
        }
        System.exit(0);
    }

    private static void handle(Extensions extensions, JSONObject request) {
        long id = request.getLongValue("id");
        JSONObject response = new JSONObject().fluentPut("id", id);
        try {
            Object result = extensions.call(request.getString("op"), request.getJSONObject("args"));
            response.put("result", result);
        } catch (Throwable error) {
            Throwable root = error;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            response.put("error", root.getClass().getSimpleName() + ": " + root.getMessage());
            response.put("kind", Errors.kind(root));
            root.printStackTrace(System.err);
        }
        reply(JSON.toJSONString(response));
    }

    private static void reply(String json) {
        synchronized (OUT) {
            try {
                out.write(json);
                out.write('\n');
                out.flush();
            } catch (Exception ignored) {
                System.exit(2);
            }
        }
    }
}
