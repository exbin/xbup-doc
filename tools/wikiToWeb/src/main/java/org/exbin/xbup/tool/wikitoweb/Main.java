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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tool to convert wiki pages to website pages.
 */
public class Main {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
//        if (args.length < 1) {
//            System.out.println("Zadejte nazev adresare jako parametr.");
//        }
//        File dir = new File(args[1]);
//        File dir = new File("/home/hajdam/Projekty/XBUP/progs/Java");
        File dir = new File("../offline_dokuwiki");
        File targetDir = new File("/home/hajdam/Projekty/xbup/docs/offline_dokuwiki_out");
//        File dir = new File("/home/hajdam/Projekty/XBUP/docs/DokuWiki/devel/projects/index.html");
        pathStart = dir.getAbsoluteFile().length();
        processFile(dir, targetDir, 0);
    }

    private static String wikiPrefix;
    private static String pathPrefix;
    private static int ul_level = 0;
    private static long pathStart = 0;

    public static String rTrim(String input) {
        int pos = input.length()-1;
        if (pos<0) return input;
        while ((pos>=0)&&(input.charAt(pos) == ' ')) pos--;
        return input.substring(0,pos+1);
    }

    public static String replaceStr(
            final String aInput,
            final String aOldPattern,
            final String aNewPattern) {
        if (aOldPattern.equals("")) {
            throw new IllegalArgumentException("Old pattern must have content.");
        }

        final StringBuffer result = new StringBuffer();
        //startIdx and idxOld delimit various chunks of aInput; these
        //chunks always end where aOldPattern begins
        int startIdx = 0;
        int idxOld = 0;
        while ((idxOld = aInput.indexOf(aOldPattern, startIdx)) >= 0) {
            //grab a part of aInput which does not include aOldPattern
            result.append(aInput.substring(startIdx, idxOld));
            //add aNewPattern to take place of aOldPattern
            result.append(aNewPattern);

            //reset the startIdx to just after the current match, to see
            //if there are any further matches
            startIdx = idxOld + aOldPattern.length();
        }
        //the final chunk will go to the end of aInput
        result.append(aInput.substring(startIdx));
        return result.toString();
    }

    public static String processParagraph(String line) {
        String result;
        if (line.startsWith("<p>")) { result = line.substring(3); } else result = line;
        result = rTrim(replaceStr(result, "&nbsp;", " "));
        int pos = 0;
        while (pos >= 0) {
            pos = result.indexOf("<", pos);
            if (pos >= 0) {
                int ePos = result.indexOf(">", pos+1);
                if (ePos == -1) throw new Error("Missing end of tag");
                String tag = result.substring(pos+1,ePos);
                if (tag.equals("p")) {
                    result = result.substring(0,pos) + System.getProperty("line.separator") + System.getProperty("line.separator") + result.substring(pos+tag.length()+1);
                } else if (tag.equals("/p")) {
                    result = result.substring(0,pos) + result.substring(pos+4);
                } else if (tag.equals("ul")) {
                    if ((pos > 0) && (!"".equals(line.substring(0,pos).trim()))) throw new Error("Tag <ul> supported only on the beggining of line");
                    result = result.substring(4+pos);
                    pos = 0;
                    ul_level++;
                } else if (tag.equals("/ul")) {
                    if (ul_level <= 0) throw new Error("Unexpected end tag </ul>");
                    ul_level--;
                    result = result.substring(0,pos) + System.getProperty("line.separator") + result.substring(pos+5);
                } else if (tag.equals("li")) {
                    if ((pos > 0) && (!"".equals(line.substring(0,pos).trim()))) throw new Error("Tag <li> supported only on the beggining of line");
                    result = "            ".substring(0,ul_level*2) + "* " + result.substring(4+pos);
                    pos = 0;
                } else if (tag.equals("/li")) {
                    result = result.substring(0,pos) + result.substring(pos+5);
                } else if (tag.startsWith("a href=\"")) {
                    String link = tag.substring(8, tag.length()-1);
                    if (link.charAt(0) == '#') {
                        // TODO?
                        result = result.substring(0,pos) + result.substring(pos+tag.length()+2);
                    } else {
                        if (!link.startsWith("http://")) {
                            String prefix = wikiPrefix;
                            while (link.startsWith("../")) {
                                link = link.substring(3);
                                prefix = prefix.substring(0,prefix.lastIndexOf(":"));
                            }
                            link = prefix + ":" + link.replace("/", ":");
                            if (link.length()>5) {
                               if (link.substring(link.length()-5).equals(".html")) link = link.substring(0,link.length()-5);
                            }
                        }
                        int bracketed = 0;
                        if (pos>0) {
                            if (result.charAt(pos-1) == '[') bracketed = 1;
                        }
                        result = result.substring(0,pos-bracketed) + "[[" + link + "|" + result.substring(pos+tag.length()+2);
                    }
                } else if (tag.startsWith("a name=\"")) {
                    result = result.substring(0,pos) + result.substring(pos+tag.length()+2);
                } else if (tag.equals("/a")) {
                    int bracketed = 0;
                    if (result.length()>pos+tag.length()+2) {
                        if (result.charAt(pos+tag.length()+2) == ']') bracketed = 1;
                    }
                    result = result.substring(0,pos) + "]]" + result.substring(pos+4+bracketed);
                } else if (tag.equals("strong")) {
                    result = result.substring(0,pos) + "**" + result.substring(pos+8);
                } else if (tag.equals("/strong")) {
                    result = result.substring(0,pos) + "**" +result.substring(pos+9);
                } else if (tag.equals("em")) {
                    result = result.substring(0,pos) + "//" + result.substring(pos+4);
                } else if (tag.equals("/em")) {
                    result = result.substring(0,pos) + "//" +result.substring(pos+5);
                } else if (tag.equals("br/")) {
                    if (ul_level>0) {
                        result = result.substring(0,pos) + " " +result.substring(pos+5);
                    } else result = result.substring(0,pos) + "\\\\" + System.getProperty("line.separator") +result.substring(pos+5);
                } else if (tag.equals("sub")||tag.equals("/sub")||tag.equals("sup")||tag.equals("/sup")) {
                    pos++;
                } else if (tag.startsWith("table")||tag.startsWith("tr")||tag.startsWith("td")||tag.startsWith("th")) {
                    pos++;
                } else if (tag.startsWith("/table")||tag.startsWith("/tr")||tag.startsWith("/td")||tag.startsWith("/th")) {
                    pos++;
                } else if (tag.startsWith("a")||tag.startsWith("acronym")||tag.startsWith("/acronym")) {
                    pos++;
                } else if (tag.startsWith("center")||tag.startsWith("/center")||tag.startsWith("img")||tag.startsWith("pre")||tag.startsWith("/pre")) {
                    pos++;
                } else if (tag.startsWith("div")||tag.startsWith("/div")||tag.startsWith("span")||tag.startsWith("/span")||tag.startsWith("p")) {
                    pos++;
                } else if (tag.equals("code")||tag.equals("/code")||tag.equals("sup")||tag.equals("sup")) {
                    pos++;
                } else if (tag.startsWith("ul")) {
                    ul_level++;
                    pos++;
                } else throw new Error("Unexpected end tag " + tag);
            }
        }
        return rTrim(result);
    }

    public static void processFile(File file, File targetFile, int level) {
        if (file.isDirectory()) {
            if (!targetFile.isDirectory()) {
                System.out.println("Directory: "+targetFile.getAbsoluteFile());
                targetFile.mkdir();
            }

            FileFilter fileFilter = new FileFilter() {
                public boolean accept(File file) {
                    return file.isDirectory() || file.getName().endsWith(".html");
                }
            };

            File[] files = file.listFiles(fileFilter);
            for (int i = 0; i < files.length; i++) {
                File outFile = new File(targetFile.getAbsoluteFile() + File.separator + files[i].getName());
                processFile(files[i], outFile, level + 1);
            }
        } else {
            {
                InputStreamReader isr = null;
                try {
                    System.out.println("File: "+targetFile.getAbsoluteFile());
                    FileInputStream source = new FileInputStream(file);
                    ul_level = 0;
                    wikiPrefix = file.getParent().substring("/home/hajdam/Projekty/XBUP/docs/DokuWiki".length());
//                    if (!wikiPrefix.isEmpty()) wikiPrefix = ":"+wikiPrefix;
                    wikiPrefix = replaceStr(wikiPrefix,"/", ":");
                    wikiPrefix = "en:doc" + wikiPrefix;
                    pathPrefix = null;
                    isr = new InputStreamReader(source, "UTF-8");
                    BufferedReader reader = new BufferedReader(isr);
                    StringBuilder mos = new StringBuilder();
                    int mode = 0;
                    while (reader.ready()) {
                        String line = reader.readLine();
                        if (mode == 0) {
                            if (line.startsWith("<title></title>")) {
                                mos.append("<title>eXtensible Binary Universal Protocol - Documentation</title>");
                                mos.append(System.getProperty("line.separator"));
                                mos.append(line.substring(15));
                                mos.append(System.getProperty("line.separator"));
                                mode = 1;
                                continue;
                            }
                        } else if (mode == 1) {
                            if (line.startsWith("<link rel=\"stylesheet")) {
                                int pos = line.indexOf("href=");
                                if (pathPrefix == null) {
                                    pathPrefix = line.substring(pos + 12, line.indexOf("css/all.css"));
                                }
                                mos.append(line.substring(0, pos + 6));
                                mos.append(line.substring(pos + 12));
                                mos.append(System.getProperty("line.separator"));
                                continue;
                            } else if (line.startsWith("<body>")) {
                                mos.append(line);
                                mos.append(System.getProperty("line.separator"));
                                mos.append("<div style=\"background-color: #AFFFAF; margin: 0px 0px 0px 0px;\"><img src=\"" + pathPrefix + "images/xbup_logo.png\" alt=\"[XBUP]\" title=\"XBUP logo\" width=\"32\" height=\"22\" align=\"left\" vspace=\"5\" hspace=\"5\" style=\"align: left; margin: 5px 5px 5px 5px;\"/><div align=\"center\" style=\"align: center; height: 32px; margin-top: 0px; margin-bottom: 0px; font-size: 24px;\"><strong>XBUP - eXtensible Binary Universal Protocol - Documentation</strong></div></div>");
                                mos.append(System.getProperty("line.separator"));
                                mos.append("<div class=\"dokuwiki export\" align=\"left\" style=\"text-align:left;\"><a href=\"");
                                if ("index.html".equals(file.getName())) {
                                    mos.append("../");
                                }
                                mos.append("../").append(file.getParentFile().getName()).append(".html\">[Up]</a></div>");
                                mos.append(System.getProperty("line.separator"));

                                mode = 2;
                                continue;
                            }
                        } else if (mode == 2) {
                            int start = 0;
                            while (start >= 0) {
                                int hrefPos = line.indexOf("href=\"../", start);
                                if (hrefPos >= 0) {
                                    long subCount = 0;
                                    boolean isStart = true;
                                    long hrefEnd = line.indexOf("\"", hrefPos + 7);
                                    start = line.indexOf("/", hrefPos + 6);
                                    while ((start >= 0)&&(start < hrefEnd)) {
                                        if (isStart) {
                                            String test = line.substring(start - 2, start);
                                            if ("..".equals(line.substring(start - 2, start))) {
                                                subCount += 2;
                                            } else {
                                                isStart = false;
                                                subCount--;
                                            }
                                        }
                                        if (subCount>0) {
                                            subCount--;
                                            start = line.indexOf("/", start + 1);
                                        } else break;
                                    }
                                    if (start > 0) {
                                        line = line.substring(0, hrefPos+6) + line.substring(start + 1);
                                        start -=  (start - hrefPos - 5);
                                    }
                                } else start = -1;
                            }

                            start = 0;
                            while (start >= 0) {
                                int hrefPos = line.indexOf("src=\"../", start);
                                if (hrefPos >= 0) {
                                    long subCount = 0;
                                    boolean isStart = true;
                                    long hrefEnd = line.indexOf("\"", hrefPos + 6);
                                    start = line.indexOf("/", hrefPos + 5);
                                    while ((start >= 0)&&(start < hrefEnd)) {
                                        if (isStart) {
                                            String test = line.substring(start - 2, start);
                                            if ("..".equals(line.substring(start - 2, start))) {
                                                subCount += 2;
                                            } else {
                                                isStart = false;
                                                subCount--;
                                            }
                                        }
                                        if (subCount>0) {
                                            subCount--;
                                            start = line.indexOf("/", start + 1);
                                        } else break;
                                    }
                                    if (start > 0) {
                                        line = line.substring(0, hrefPos+5) + line.substring(start + 1);
                                        start -=  (start - hrefPos - 4);
                                    }
                                } else start = -1;
                            }
                        } else if (mode == 3) {
                            if (file.getName().equals("index.html")) {
                                if (line.startsWith("<h3>Content</h3>")) {
                                    mode = 4;
                                    mos.append(System.getProperty("line.separator"));
                                    mos.append("===== Content =====");
                                    mos.append(System.getProperty("line.separator"));
                                } else if (line.startsWith("<h3>Obsah</h3>")) {
                                    mode = 4;
                                    mos.append(System.getProperty("line.separator"));
                                    mos.append("===== Obsah =====");
                                    mos.append(System.getProperty("line.separator"));
                                }
                            } else {
                                if (line.startsWith("</p>")) {
                                    mode = 5;
                                }
                            }
                        } else if (mode == 4) {
                            if (!"".equals(line.trim())) {
                                int pos = line.indexOf("<a href=");
                                if (pos >= 0) {
                                    int ePos = line.indexOf("\"", pos + 9);
                                    String link = line.substring(pos + 9, ePos);
                                    link = wikiPrefix + ":" + link.replace("/", ":");
                                    if (link.length()>5) {
                                       if (link.substring(link.length()-5).equals(".html")) link = link.substring(0,link.length()-5);
                                    }
                                    String rest = line.substring(ePos+2);
                                    rest = replaceStr(replaceStr(replaceStr(rest, "&nbsp;", " "),"<br/>", ""),"</a>", "]]");
                                    mos.append("            ".substring(0,(pos / 6)+2) + "* [[" + link + "|" + rest);
                                    mos.append(System.getProperty("line.separator"));
                                }
                            } else mode = 6;
                        } else if (mode == 5) {
                            if (line.equals("<hr/>")) {
                                mode = 6;
                            } else if (line.startsWith("<h3>")) {
                                int pos = line.indexOf(">",5);
                                pos = line.indexOf(" ",pos);
                                int ePos = line.indexOf("</h3>");
                                if ((pos == -1)||(ePos == -1)) throw new Error("Missing space in header line "+line);
                                mos.append(System.getProperty("line.separator"));
                                int dashPos = 0;
                                int subLevel = 0;
                                while ((dashPos < pos)&&(dashPos>=0)) {
                                    dashPos = line.indexOf("-",dashPos);
                                    if (dashPos>=0) {
                                        dashPos++;
                                        subLevel++;
                                    }
                                }
                                subLevel = 5-subLevel;
                                char[] mark = new char[subLevel];
                                Arrays.fill(mark, '=');
                                mos.append(String.valueOf(mark)+" " + line.substring(pos+1,ePos) + " "+ String.valueOf(mark));
                                mos.append(System.getProperty("line.separator"));
                            } else if (!line.equals("")) {
                                mos.append(processParagraph(line));
                                mos.append(System.getProperty("line.separator"));
                            }
                        } else if (mode == 6) {
                        }
                        mos.append(line);
                        mos.append(System.getProperty("line.separator"));
                    }
                    mos.append(System.getProperty("line.separator"));
                    mos.append("<hr/>");
                    mos.append(System.getProperty("line.separator"));
                    mos.append("<p>Homepage: <a href=\"http://xbup.org\">http://xbup.org</a><br/>");
                    mos.append(System.getProperty("line.separator"));
                    mos.append("License: GNU Free Documentation License (FDL)</p>");
                    mos.append(System.getProperty("line.separator"));
                    mos.append("</body></html>");
                    mos.append(System.getProperty("line.separator"));
                    isr.close();
                    // if (mode < 5) throw new Error("Processing error (mode: " + String.valueOf(mode)+")");
                    FileOutputStream fos = new FileOutputStream(targetFile);
                    OutputStreamWriter out = new OutputStreamWriter(fos, "UTF-8");
                    out.write(mos.toString());
                    out.close();
                } catch (UnsupportedEncodingException ex) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                } catch (FileNotFoundException ex) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IOException ex) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }
}
