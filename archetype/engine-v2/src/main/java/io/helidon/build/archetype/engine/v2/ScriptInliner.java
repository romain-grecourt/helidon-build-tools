/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.build.archetype.engine.v2;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import static io.helidon.build.common.Checksum.md5;

final class ScriptInliner extends ScriptInvoker {
    private final Deque<Node> callStack = new ArrayDeque<>();
    private final Map<String, Node> methods = new HashMap<>();
    private final Map<Node, Path> workDirs = new HashMap<>();

    ScriptInliner(Context ctx) {
        super(ctx);
    }

    Node inline(Script.Source source) {
        Node node = load(Script.Loader.EMPTY, source);
        invoke(node);
        return node;
    }

    Path workDir(Node node) {
        return workDirs.get(node);
    }

    @Override
    protected Node load(Script.Loader loader, Script.Source source) {
        // disable caching to get unique nodes for each invocation
        return source.readScript(false, Script.Loader.EMPTY);
    }

    @Override
    public boolean visit(Node node) {
        switch (node.kind()) {
            case CALL:
                Script script = node.script();
                String method = node.attribute("method").getString();
                Node prototype = methods.computeIfAbsent(script.path() + ":" + method, k -> script.methods().get(method));
                if (prototype == null) {
                    throw new IllegalStateException("Method not found: " + method);
                }
                String hash = md5(node.location());
                script.methods().put(hash, prototype.deepCopy().attribute("name", hash));
                callStack.push(node.attribute("method", hash));
                break;
            case SOURCE:
            case EXEC:
                if (node.attribute("url").isPresent()) {
                    // skip url invocation
                    return false;
                }
                callStack.push(node);
                break;
            case FILE:
            case TEMPLATE:
            case FILES:
            case TEMPLATES:
            case OUTPUT:
                workDirs.put(node, context().cwd());
                break;
            case CONDITION:
            case VARIABLE_TEXT:
            case VARIABLE_ENUM:
            case VARIABLE_BOOLEAN:
            case VARIABLE_LIST:
            case PRESET_TEXT:
            case PRESET_ENUM:
            case PRESET_BOOLEAN:
            case PRESET_LIST:
                return true;
            default:
        }
        return super.visit(node);
    }

    @Override
    public void postVisit(Node node) {
        switch (node.kind()) {
            case CALL:
                String method = node.attribute("method").getString();
                node.script().methods().remove(method);
                break;
            case SOURCE:
            case EXEC:
                if (node.attribute("url").isPresent()) {
                    // skip url invocation
                    return;
                }
                break;
            case METHOD:
            case SCRIPT:
                if (!callStack.isEmpty()) {
                    callStack.pop().replace(node.children());
                }
                break;
            default:
        }
        super.postVisit(node);
    }
}
