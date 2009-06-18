/*
 * Copyright 1999-2005 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* $Id$ */

package org.apache.fop.layoutmgr;

import org.apache.fop.datatypes.PercentBase;
import org.apache.fop.fo.pagination.Flow;
import org.apache.fop.area.Area;
import org.apache.fop.area.BlockParent;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * LayoutManager for an fo:flow object.
 * Its parent LM is the PageSequenceLayoutManager.
 * This LM is responsible for getting columns of the appropriate size
 * and filling them with block-level areas generated by its children.
 */
public class FlowLayoutManager extends BlockStackingLayoutManager
                               implements BlockLevelLayoutManager {
    private Flow fobj;
    
    /** List of break possibilities */
    protected List blockBreaks = new java.util.ArrayList();

    /** Array of areas currently being filled stored by area class */
    private BlockParent[] currentAreas = new BlockParent[Area.CLASS_MAX];

    private int iStartPos = 0;

    /**
     * Used to count the number of subsequent times to layout child areas on
     * multiple pages.
     */
    private int numSubsequentOverflows = 0;
    
/*LF*/
    private static class StackingIter extends PositionIterator {
        StackingIter(Iterator parentIter) {
            super(parentIter);
        }

        protected LayoutManager getLM(Object nextObj) {
            return ((Position) nextObj).getLM();
        }

        protected Position getPos(Object nextObj) {
            return ((Position) nextObj);
        }
    }
/*LF*/

    /**
     * This is the top level layout manager.
     * It is created by the PageSequence FO.
     * @param node Flow object
     */
    public FlowLayoutManager(Flow node) {
        super(node);
        fobj = node;
    }

    /*
    public BreakPoss getNextBreakPoss(LayoutContext context) {

        // currently active LM
        LayoutManager curLM;
        MinOptMax stackSize = new MinOptMax();

        fobj.setLayoutDimension(PercentBase.BLOCK_IPD, context.getRefIPD());
        fobj.setLayoutDimension(PercentBase.BLOCK_BPD, context.getStackLimit().opt);

        while ((curLM = getChildLM()) != null) {
            if (curLM.generatesInlineAreas()) {
                log.error("inline area not allowed under flow - ignoring");
                curLM.setFinished(true);
                continue;
            }

            // Make break positions and return page break
            // Set up a LayoutContext
            MinOptMax bpd = context.getStackLimit();
            BreakPoss bp;

            LayoutContext childLC = new LayoutContext(0);
            boolean breakPage = false;
            childLC.setStackLimit(MinOptMax.subtract(bpd, stackSize));
            childLC.setRefIPD(context.getRefIPD());

            if (!curLM.isFinished()) {
                if ((bp = curLM.getNextBreakPoss(childLC)) != null) {
                    stackSize.add(bp.getStackingSize());
                    blockBreaks.add(bp);
                    // set stackLimit for remaining space
                    childLC.setStackLimit(MinOptMax.subtract(bpd, stackSize));

                    if (bp.isForcedBreak() || bp.nextBreakOverflows()) {
                        if (log.isDebugEnabled()) {
                            log.debug("BreakPoss signals " + (bp.isForcedBreak() 
                                    ? "forced break" : "next break overflows"));
                        }
                        breakPage = true;
                    }
                }
            }

            // check the stack bpd and if greater than available
            // height then go to the last best break and return
            // break position
            if (stackSize.opt > context.getStackLimit().opt) {
                breakPage = true;
            }
            if (breakPage) {
                numSubsequentOverflows++;
                if (numSubsequentOverflows > 50) {
                    log.error("Content overflows available area. Giving up after 50 attempts.");
                    setFinished(true);
                    return null;
                }
                return new BreakPoss(
                      new LeafPosition(this, blockBreaks.size() - 1));
            }
            numSubsequentOverflows = 0; //Reset emergency counter
        }
        setFinished(true);
        if (blockBreaks.size() > 0) {
            return new BreakPoss(
                             new LeafPosition(this, blockBreaks.size() - 1));
        }
        return null;
    }*/


    /**
     * "wrap" the Position inside each element moving the elements from 
     * SourceList to targetList
     * @param sourceList source list
     * @param targetList target list receiving the wrapped position elements
     */
    protected void wrapPositionElements(List sourceList, List targetList) {
        ListIterator listIter = sourceList.listIterator();
        while (listIter.hasNext()) {
            KnuthElement tempElement;
            tempElement = (KnuthElement) listIter.next();
            //if (tempElement.getLayoutManager() != this) {
            tempElement.setPosition(new NonLeafPosition(this,
                    tempElement.getPosition()));
            //}
            targetList.add(tempElement);
        }
    }

    
//TODO Reintroduce emergency counter (generate error to avoid endless loop)
    public LinkedList getNextKnuthElements(LayoutContext context, int alignment) {
        // set layout dimensions
        fobj.setLayoutDimension(PercentBase.BLOCK_IPD, context.getRefIPD());
        fobj.setLayoutDimension(PercentBase.BLOCK_BPD, context.getStackLimit().opt);

        // currently active LM
        BlockLevelLayoutManager curLM;
        BlockLevelLayoutManager prevLM = null;
        //MinOptMax stackSize = new MinOptMax();
        LinkedList returnedList;
        LinkedList returnList = new LinkedList();

        while ((curLM = ((BlockLevelLayoutManager) getChildLM())) != null) {
            if (curLM.generatesInlineAreas()) {
                log.error("inline area not allowed under flow - ignoring");
                curLM.setFinished(true);
                continue;
            }

            // Set up a LayoutContext
            //MinOptMax bpd = context.getStackLimit();

            LayoutContext childLC = new LayoutContext(0);
            childLC.setStackLimit(context.getStackLimit());
            childLC.setRefIPD(context.getRefIPD());

            // get elements from curLM
            returnedList = curLM.getNextKnuthElements(childLC, alignment);
            //log.debug("FLM.getNextKnuthElements> returnedList.size() = " + returnedList.size());

            // "wrap" the Position inside each element
            LinkedList tempList = returnedList;
            returnedList = new LinkedList();
            wrapPositionElements(tempList, returnedList);

            if (returnedList.size() == 1
                && ((KnuthElement)returnedList.getFirst()).isPenalty()
                && ((KnuthPenalty)returnedList.getFirst()).getP() == -KnuthElement.INFINITE) {
                // a descendant of this flow has break-before
                returnList.addAll(returnedList);
                return returnList;
            } else {
                if (returnList.size() > 0) {
                    // there is a block before this one
                    if (prevLM.mustKeepWithNext()
                        || curLM.mustKeepWithPrevious()) {
                        // add an infinite penalty to forbid a break between blocks
                        returnList.add(new KnuthPenalty(0, KnuthElement.INFINITE, false, new Position(this), false));
                    } else if (!((KnuthElement) returnList.getLast()).isGlue()) {
                        // add a null penalty to allow a break between blocks
                        returnList.add(new KnuthPenalty(0, 0, false, new Position(this), false));
                    }
                }
/*LF*/          if (returnedList.size() > 0) { // controllare!
                    returnList.addAll(returnedList);
                    if (((KnuthElement)returnedList.getLast()).isPenalty()
                        && ((KnuthPenalty)returnedList.getLast()).getP() == -KnuthElement.INFINITE) {
                        // a descendant of this flow has break-after
/*LF*/                  //System.out.println("FLM - break after!!");
                        return returnList;
                    }
/*LF*/          }
            }
            prevLM = curLM;
        }

        setFinished(true);

        if (returnList.size() > 0) {
            return returnList;
        } else {
            return null;
        }
    }

    public int negotiateBPDAdjustment(int adj, KnuthElement lastElement) {
        log.debug(" FLM.negotiateBPDAdjustment> " + adj);

        if (lastElement.getPosition() instanceof NonLeafPosition) {
            // this element was not created by this FlowLM
            NonLeafPosition savedPos = (NonLeafPosition)lastElement.getPosition();
            lastElement.setPosition(savedPos.getPosition());
            int returnValue = ((BlockLevelLayoutManager) lastElement.getLayoutManager()).negotiateBPDAdjustment(adj, lastElement);
            lastElement.setPosition(savedPos);
            log.debug(" FLM.negotiateBPDAdjustment> result " + returnValue);
            return returnValue;
        } else {
            return 0;
        }
    }

    public void discardSpace(KnuthGlue spaceGlue) {
        log.debug(" FLM.discardSpace> ");

        if (spaceGlue.getPosition() instanceof NonLeafPosition) {
            // this element was not created by this FlowLM
            NonLeafPosition savedPos = (NonLeafPosition)spaceGlue.getPosition();
            spaceGlue.setPosition(savedPos.getPosition());
            ((BlockLevelLayoutManager) spaceGlue.getLayoutManager()).discardSpace(spaceGlue);
            spaceGlue.setPosition(savedPos);
        }
    }

    public boolean mustKeepTogether() {
        return false;
    }

    public boolean mustKeepWithPrevious() {
        return false;
    }

    public boolean mustKeepWithNext() {
        return false;
    }

    public LinkedList getChangedKnuthElements(List oldList, /*int flaggedPenalty,*/ int alignment) {
        ListIterator oldListIterator = oldList.listIterator();
        KnuthElement returnedElement;
        LinkedList returnedList = new LinkedList();
        LinkedList returnList = new LinkedList();
        KnuthElement prevElement = null;
        KnuthElement currElement = null;
        int fromIndex = 0;

/*LF*/  //System.out.println("");
/*LF*/  //System.out.println("FLM.getChangedKnuthElements> prima dell'unwrap, oldList.size() = " + oldList.size() + " da 0 a " + (oldList.size() - 1));
        // "unwrap" the Positions stored in the elements
        KnuthElement oldElement;
        while (oldListIterator.hasNext()) {
            oldElement = (KnuthElement)oldListIterator.next();
            if (oldElement.getPosition() instanceof NonLeafPosition) {
                // oldElement was created by a descendant of this FlowLM
                oldElement.setPosition(((NonLeafPosition)oldElement.getPosition()).getPosition());
            } else {
                // thisElement was created by this FlowLM, remove it
                oldListIterator.remove();
            }
        }
        // reset the iterator
        oldListIterator = oldList.listIterator();

/*LF*/  //System.out.println("FLM.getChangedKnuthElements> dopo l'unwrap, oldList.size() = " + oldList.size() + " da 0 a " + (oldList.size() - 1));

        while (oldListIterator.hasNext()) {
            currElement = (KnuthElement) oldListIterator.next();
/*LF*/      //System.out.println("elemento n. " + oldListIterator.previousIndex() + " nella oldList");
            if (prevElement != null
                && prevElement.getLayoutManager() != currElement.getLayoutManager()) {
                // prevElement is the last element generated by the same LM
                BlockLevelLayoutManager prevLM = (BlockLevelLayoutManager)
                                                 prevElement.getLayoutManager();
                BlockLevelLayoutManager currLM = (BlockLevelLayoutManager)
                                                 currElement.getLayoutManager();
/*LF*/          //System.out.println("FLM.getChangedKnuthElements> chiamata da " + fromIndex + " a " + oldListIterator.previousIndex());
                returnedList.addAll(prevLM.getChangedKnuthElements(oldList.subList(fromIndex, oldListIterator.previousIndex()),
                                                                   /*flaggedPenalty,*/ alignment));
                fromIndex = oldListIterator.previousIndex();

                // there is another block after this one
                if (prevLM.mustKeepWithNext()
                    || currLM.mustKeepWithPrevious()) {
                    // add an infinite penalty to forbid a break between blocks
                    returnedList.add(new KnuthPenalty(0, KnuthElement.INFINITE, false, new Position(this), false));
                } else if (!((KnuthElement) returnedList.getLast()).isGlue()) {
                    // add a null penalty to allow a break between blocks
                    returnedList.add(new KnuthPenalty(0, 0, false, new Position(this), false));
                }
            }
            prevElement = currElement;
        }
        if (currElement != null) {
            BlockLevelLayoutManager currLM = (BlockLevelLayoutManager)
                                             currElement.getLayoutManager();
/*LF*/      //System.out.println("FLM.getChangedKnuthElements> chiamata da " + fromIndex + " a " + oldList.size());
            returnedList.addAll(currLM.getChangedKnuthElements(oldList.subList(fromIndex, oldList.size()),
                                                               /*flaggedPenalty,*/ alignment));
        }

        // "wrap" the Position stored in each element of returnedList
        // and add elements to returnList
        ListIterator listIter = returnedList.listIterator();
        while (listIter.hasNext()) {
            returnedElement = (KnuthElement)listIter.next();
            if (returnedElement.getLayoutManager() != this) {
                returnedElement.setPosition(new NonLeafPosition(this, returnedElement.getPosition()));
            }
            returnList.add(returnedElement);
        }

        return returnList;
    }

    /**
     * @see org.apache.fop.layoutmgr.LayoutManager#addAreas(PositionIterator, LayoutContext)
     */
    public void addAreas(PositionIterator parentIter, LayoutContext layoutContext) {
        AreaAdditionUtil.addAreas(parentIter, layoutContext);
        /*
        LayoutManager childLM = null;
        LayoutContext lc = new LayoutContext(0);
        LayoutManager firstLM = null;
        LayoutManager lastLM = null;

        // "unwrap" the NonLeafPositions stored in parentIter
        // and put them in a new list; 
        LinkedList positionList = new LinkedList();
        Position pos;
        while (parentIter.hasNext()) {
            pos = (Position)parentIter.next();
            if (pos instanceof NonLeafPosition) {
                // pos was created by a child of this FlowLM
                positionList.add(((NonLeafPosition) pos).getPosition());
                lastLM = ((NonLeafPosition) pos).getPosition().getLM();
                if (firstLM == null) {
                    firstLM = lastLM;
                }
            } else {
                // pos was created by this FlowLM, so it must be ignored
            }
        }

        StackingIter childPosIter = new StackingIter(positionList.listIterator());
        while ((childLM = childPosIter.getNextChildLM()) != null) {
            // Add the block areas to Area
            lc.setFlags(LayoutContext.FIRST_AREA, childLM == firstLM);
            lc.setFlags(LayoutContext.LAST_AREA, childLM == lastLM);
            // set space before for the first LM, in order to implement
            // display-align = center or after
            lc.setSpaceBefore((childLM == firstLM ? layoutContext.getSpaceBefore() : 0));
            // set space after for each LM, in order to implement
            // display-align = distribute
            lc.setSpaceAfter(layoutContext.getSpaceAfter());
            lc.setStackLimit(layoutContext.getStackLimit());
            childLM.addAreas(childPosIter, lc);
        }*/

        flush();
    }

    /**
     * Add child area to a the correct container, depending on its
     * area class. A Flow can fill at most one area container of any class
     * at any one time. The actual work is done by BlockStackingLM.
     * @see org.apache.fop.layoutmgr.LayoutManager#addChildArea(Area)
     */
    public void addChildArea(Area childArea) {
        addChildToArea(childArea,
                          this.currentAreas[childArea.getAreaClass()]);
    }

    /**
     * @see org.apache.fop.layoutmgr.LayoutManager#getParentArea(Area)
     */
    public Area getParentArea(Area childArea) {
        // Get an area from the Page
        BlockParent parentArea = (BlockParent)parentLM.getParentArea(childArea);
        this.currentAreas[parentArea.getAreaClass()] = parentArea;
        setCurrentArea(parentArea);
        return parentArea;
    }

    /**
     * @see org.apache.fop.layoutmgr.LayoutManager#resetPosition(Position)
     */
    public void resetPosition(Position resetPos) {
        if (resetPos == null) {
            reset(null);
        }
    }
}
