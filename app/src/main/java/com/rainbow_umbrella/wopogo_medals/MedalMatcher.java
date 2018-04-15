package com.rainbow_umbrella.wopogo_medals;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;

public class MedalMatcher {
    public ArrayList<Medal> mMedalList;
    public MedalMatcher( ArrayList<Medal> medalList) {
        mMedalList = medalList;
    }

    public int checkName(String name) {
        for (int i = 0; i < mMedalList.size(); i++) {
            if (name.equals( mMedalList.get(i).mName)) {
                return i;
            }
        }
        return -1;
    }

    public int getValue(String value) {
        int result = -1;
        if (value.indexOf('%') >= 0) {
            return -1;
        }
        String partResult = new String();
        int i = 0;
        final String matchChars="0123456789,.";
        while (i < value.length() && matchChars.indexOf(value.charAt(i)) >= 0) {
            char thisChar = value.charAt(i);
            if (thisChar != ',' && thisChar != '.') {
                partResult += thisChar;
            }
            i++;
        }
        if (partResult.length() > 0) {
            result = Integer.parseInt(partResult);
        }
        return result;
    }
    public Medal getMatch(String textBlocks) {
        Medal result = null;
        boolean nameFound = false;
        boolean valueFound = false;
        String nameResult = "";
        int valueResult = -1;
        try {
            JSONArray blocks = new JSONArray(textBlocks);
            for(int i = 0; i < blocks.length(); i++) {
                JSONArray block = new JSONArray(blocks.get(i).toString());
                if (block.length() > 0) {
                    String firstLine = block.get(0).toString();
                    firstLine.replace("\"", "");
                    if (checkName(firstLine) >= 0) {
                        nameFound = true;
                        nameResult = firstLine;
                        // If blocks are from top of screen next block will have the number.
                        if (i < blocks.length() - 1) {
                            JSONArray nextBlock = new JSONArray(blocks.get(i + 1).toString());
                            if (nextBlock.length() > 0) {
                                String nextFirstLine = nextBlock.get(0).toString();
                                int tmpValue = getValue(nextFirstLine);
                                if (tmpValue >= 0) {
                                    valueFound = true;
                                    valueResult = tmpValue;
                                }
                                // Special case for Johto which sees a "O" in the symbol.
                                if (!valueFound && nextFirstLine.equals("O") && i < blocks.length() - 2) {
                                    JSONArray nextNextBlock = new JSONArray(blocks.get(i + 2).toString());
                                    if (nextNextBlock.length() > 0) {
                                        String nextNextFirstLine = nextNextBlock.get(0).toString();
                                        tmpValue = getValue(nextNextFirstLine);
                                        if (tmpValue >= 0) {
                                            valueFound = true;
                                            valueResult = tmpValue;
                                        }
                                    }
                                }
                            }
                        }
                        // Sometimes the reader returns blocks from the bottom of the screen so check
                        // the previous block.
                        if (i > 0 && !valueFound) {
                            JSONArray previousBlock = new JSONArray(blocks.get(i - 1).toString());
                            if (previousBlock.length() > 0) {
                                String previousFirstLine = previousBlock.get(0).toString();
                                int tmpValue = getValue(previousFirstLine);
                                if (tmpValue >= 0) {
                                    valueFound = true;
                                    valueResult = tmpValue;
                                }
                                // Special case for Johto which sees a "O" in the symbol.
                                if (!valueFound && previousFirstLine.equals("O") && i > 1) {
                                    JSONArray prevPrevBlock = new JSONArray(blocks.get(i - 2).toString());
                                    if (prevPrevBlock.length() > 0) {
                                        String prevPrevFirstLine = prevPrevBlock.get(0).toString();
                                        tmpValue = getValue(prevPrevFirstLine);
                                        if (tmpValue >= 0) {
                                            valueFound = true;
                                            valueResult = tmpValue;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (nameFound && valueFound) {
                if (nameResult.equals("Jogger")) {
                    valueResult /= 10;
                }
                result = new Medal(nameResult, valueResult);
            }
        } catch (JSONException e) {

        }
        return result;
    }
}
