/*
 * Copyright (C) ExBin Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.exbin.xbup.tool.wikitoweb;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/**
 * Tool to convert wiki pages to website pages.
 */
public class Main {

    private static final String OUTPUT_FILES_DIR = "output";

    public static void main(String[] args) {
        File startDir = new File("../../xbup-doc.wiki");
        File targetDir = new File("out");

        FileFilter fileFilter = (File file) -> file.getName().endsWith(".md");

        File[] files = startDir.listFiles(fileFilter);
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            // TODO Convert paths
            String fileName = file.getName();
            String outputFilePath = fileName.substring(0, fileName.length() - 3);
            // File outFile = new File(targetDir + File.separator + files[i].getName());
            processFile(file, outputFilePath + ".php");
        }
    }

    private static void processFile(File sourceFile, String outputFilePath) {
        File outputFile = new File(OUTPUT_FILES_DIR + File.separator + outputFilePath);
        // outputFile.mkdirs();
        State state = State.START;
        State preSkip = state;
        boolean hasMark = true;
        boolean qeuedBreak = false;

        Set<String> excludeChapters = getChapters();
        Set<String> usedImages = new HashSet<>();

        // Write modified file
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            OutputStreamWriter out = new OutputStreamWriter(fos, "UTF-8");

            out.write("<div id=\"content\">\n");
            out.write("<?php\n");
            out.write("include \'pages/inc/doc.php\'\n");
            out.write("showNavigation();\n");
            out.write("?>\n");

            try (FileInputStream inputStream = new FileInputStream(sourceFile)) {
                InputStreamReader isr = new InputStreamReader(inputStream, "UTF-8");
                BufferedReader reader = new BufferedReader(isr);
                while (reader.ready()) {
                    String line = reader.readLine();
                    if (qeuedBreak) {
                        if (!line.isEmpty()) {
                            out.append("<br/>\n");
                        }
                        qeuedBreak = false;
                    }

                    if (hasMark && line.startsWith("**")) {
                        state = switchState(out, state, State.BREAK);
                        out.write("<p style=\"color: red; font-weight: bold;\">" + line.substring(2, line.length() - 2) + "</p>\n");
                        hasMark = false;
                        continue;
                    }
                    if (line.startsWith("#")) {
                        int level = '#' == line.charAt(2) ? 3 : ('#' == line.charAt(1) ? 2 : 1);
                        String chapterName = line.substring(level + 1);
                        String chapterId = getChapterId(chapterName);

                        if (excludeChapters.contains(chapterId)) {
                            preSkip = State.BREAK;
                            state = switchState(out, state, State.SKIP);
                        } else {
                            if (state == State.SKIP) {
                                state = switchState(out, state, preSkip);
                            }

                            state = switchState(out, state, State.CHAPTER);
                            out.write("<h" + level + " id=\"" + chapterId + "\">" + chapterName + "</h" + level + ">\n");
                        }
                        continue;
                    }

                    if (state == State.SKIP) {
                        continue;
                    }

                    if (line.isEmpty()) {
                        state = switchState(out, state, State.BREAK);
                    } else if (line.startsWith("* ")) {
                        state = switchState(out, state, State.LIST);
                        out.write("<li>" + processText(line.substring(2), State.LIST) + "</li>\n");
                    } else if (line.startsWith("    ")) {
                        if (state == State.CODE) {
                            out.write("\n");
                        } else {
                            state = switchState(out, state, State.CODE);
                        }
                        out.write(processText(line.substring(4), State.CODE));
                    } else if (line.startsWith("!")) {
                        state = switchState(out, state, State.IMAGE);
                        int breakPos = line.indexOf("]");
                        String title = line.substring(2, breakPos);
                        String path = line.substring(breakPos + 2, line.length() - 1);
                        usedImages.add(path);
                        out.write("<p><img class=\"center\" src=\"" + path + "\" title=\"" + title + "\" alt=\"" + title + "\" /></p>\n");
                    } else {
                        if (line.endsWith("  ")) {
                            qeuedBreak = true;
                            line = line.substring(0, line.length() - 2);
                        }
                        state = switchState(out, state, State.PARAGRAPH);
                        out.write(processText(line, State.PARAGRAPH));
                    }
                }
            } catch (IOException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            switchState(out, state, State.END);

            out.write("\n\n</div>\n");
            out.write("</body>\n");
            out.write("</html>\n");

            out.close();
        } catch (IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Nonnull
    private static Set<String> getChapters() {
        Set<String> chapters = new HashSet<>();
        /*        File chaptersFile = new File(MAP_FILES_DIR + projectVariant.getId().toLowerCase() + ".cfg");

        try (FileInputStream inputStream = new FileInputStream(chaptersFile)) {
            InputStreamReader isr = new InputStreamReader(inputStream, "UTF-8");
            BufferedReader reader = new BufferedReader(isr);
            while (reader.ready()) {
                String line = reader.readLine();
                if (!line.isEmpty()) {
                    chapters.add(line);
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        } */
        return chapters;
    }

    @Nonnull
    private static String getChapterId(String chapterName) {
        return chapterName.toLowerCase().replace(' ', '-');
    }

    @Nonnull
    private static State switchState(OutputStreamWriter out, State oldState, State state) throws IOException {
        if (oldState == state) {
            return state;
        }

        if ((state == State.SKIP) || (oldState == State.SKIP)) {
            return state;
        }

        switch (oldState) {
            case PARAGRAPH:
                out.write("</p>\n");
                break;
            case LIST:
                out.write("</ul>\n");
                break;
            case CODE:
                out.write("</code></pre>\n");
                break;
            default:
                break;
        }

        switch (state) {
            case PARAGRAPH:
                out.write("<p>");
                break;
            case LIST:
                out.write("<ul>\n");
                break;
            case CODE:
                out.write("<pre style=\"background-color: rgb(246, 248, 250); padding: 8px;\"><code>");
                break;
            default:
                break;
        }

        return state;
    }

    private static final String matches = "\"\'`&<>~[";

    @Nonnull
    private static String processText(String text, State state) {
        StringBuilder output = new StringBuilder();
        int pos = 0;
        int batch = 0;
        int length = text.length();
        while (pos < length) {
            char next = text.charAt(pos);
            int match = matches.indexOf(next);
            if (match >= 0) {
                if (batch > 0) {
                    output.append(text.substring(pos - batch, pos));
                    batch = 0;
                }
                switch (match) {
                    case 0: {
                        output.append("&quot;");
                        break;
                    }
                    case 1: {
                        output.append("&#39;");
                        break;
                    }
                    case 2: {
                        output.append("&quot;");
                        break;
                    }
                    case 3: {
                        output.append("&amp;");
                        break;
                    }
                    case 4: {
                        output.append("&lt;");
                        break;
                    }
                    case 5: {
                        output.append("&gt;");
                        break;
                    }
                    case 6: {
                        if (pos + 1 < length && '~' == text.charAt(pos + 1)) {
                            int endPos = text.indexOf("~~", pos + 2);
                            output.append("<strike>").append(text.substring(pos + 2, endPos)).append("</strike>");
                            pos = endPos + 1;
                        }
                        break;
                    }
                    case 7: {
                        if (state != State.CODE) {
                            int breakPos = text.indexOf("]", pos);
                            if (breakPos < 0) {
                                // Broken
                                output.append("[");
                            } else {
                                int endPos = text.indexOf(")", breakPos);
                                if (endPos < 0) {
                                    // Broken
                                    output.append("[");
                                } else {
                                    String linkText = text.substring(pos + 1, breakPos);
                                    String linkTarget = text.substring(breakPos + 2, endPos);
                                    boolean isExt = linkTarget.startsWith("https://");
                                    output.append("<a ");
                                    if (isExt) {
                                        output.append("class=\"urlextern\" ");
                                    }
                                    output.append(" href=\"").append(linkTarget).append("\">").append(linkText).append("</a>");
                                    pos = endPos;
                                }
                            }
                        } else {
                            output.append("[");
                        }
                        break;
                    }
                }
            } else {
                batch++;
            }
            pos++;
        }
        if (batch > 0) {
            output.append(text.substring(length - batch));
        }
        return output.toString();
    }

    private static enum State {
        START,
        SKIP,
        CHAPTER,
        PARAGRAPH,
        BREAK,
        LIST,
        TABLE,
        CODE,
        IMAGE,
        END
    }

    private static enum Align {
        LEFT, RIGHT, CENTER
    }
}
