package com.rainbow_umbrella.wopogo_medals;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
/*
 *  MedalMatcher
 *
 *  Attempt to match text blocks to a medal label and value. Input is a JSON string of text blocks.
 *  The blocks are compared to a list of string values for the medals. If the start of the text
 *  block matches the medal string and the subsequent block is an integer success is assumed. The
 *  text blocks are not assured to be in the correct order and also special cases have been added
 *  for "Johto" and "Berry Master" where background images match the strings "O" and "C" respectively
 *
 */
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
        // "." and "," characters are ignored. "," is used for thousand separators by the game and
        // the OCR does not distinguish between "." and "," well. Note, this then means that the
        // medal "Jogger must be divided by ten as it displays in kilometres a value stored to the
        // nearest 100 metres.
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
                                if (!valueFound &&
                                        (nextFirstLine.equals("O") || nextFirstLine.equals("C")) &&
                                        i < blocks.length() - 2) {
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
                                if (!valueFound &&
                                        (previousFirstLine.equals("O") || previousFirstLine.equals("C")) &&
                                        i > 1) {
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
                    // Stored to the nearest 100 metres.
                    valueResult /= 10;
                }
                result = new Medal(nameResult, valueResult);
            }
        } catch (JSONException e) {

        }
        return result;
    }
}
