package org.basex.gui.view.tree;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Transparency;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import javax.swing.SwingUtilities;
import org.basex.data.Data;
import org.basex.data.Nodes;
import org.basex.gui.GUIConstants;
import org.basex.gui.GUIProp;
import org.basex.gui.layout.BaseXLayout;
import org.basex.gui.layout.BaseXPopup;
import org.basex.gui.view.View;
import org.basex.gui.view.ViewNotifier;
import org.basex.gui.view.ViewRect;
import org.basex.util.IntList;
import org.basex.util.Performance;
import org.basex.util.Token;

/**
 * This class offers a real tree view.
 * @author Workgroup DBIS, University of Konstanz 2005-08, ISC License
 * @author Wolfgang Miller
 */
public final class TreeView extends View {
  /** Current font height. */
  private int fontHeight;
  /** The sum of all size values of a node line. */
  int sumNodeSizeInLine;
  /** Array-list of array-list with the current rectangles. */
  private ArrayList<ArrayList<TreeRect>> rectsPerLevel;

  // Constants
  /** Color for nodes. **/
  private final int colorNode = 0xEDEFF7;
  /** Color for rectangles. **/
  private final int colorRect = 0xC9CFE7;
  /** Color distance per level. **/
  private final int colorDiff = 0x121008;
  /** Color marked nodes. **/
  private final Color markColor = new Color(0x035FC7);
  /** Color text-nodes. **/
  private final Color textColor = new Color(0x000F87);
  /** Color highlighted nodes. **/
  private final Color highlightColor = new Color(0x5D6FB7);
  /** Minimum space in rectangles needed for tags. **/
  private final int minSpace = 35;
  /** Minimum space between the levels. **/
  final int minimumLevelDistance = 5;

  // Options
  /** Draw only element nodes. */
  private final boolean onlyElementNodes = false;
  /** Show parent node. */
  private final boolean showParentNode = true;
  /** Show children nodes. */
  private final boolean showChildNodes = true;
  /** Always draw same node space. */
  private final boolean consistentNodeSpace = true;
  /** Sum of spaces in current node line. */
  private int nodeSpacesPerLine = -1;

  /** Nodes in current line. */
  private int nodeCount;
  /** Current mouse position x. */
  private int mousePosX = -1;
  /** Current mouse position y. */
  private int mousePosY = -1;
  /** Window width. */
  private int wwidth = -1;
  /** Window height. */
  private int wheight = -1;
  /** Currently focused rectangle. */
  private TreeRect focusedRect;
  /** Level of currently focused rectangle. */
  private int focusedRectLevel = -1;
  /** Current Image of visualization. */
  private BufferedImage treeImage;
  /** Depth of the document. */
  // private int documentDepth = -1;
  /** Notified focus flag. */
  private boolean refreshedFocus;
  /** Notified mark flag. */
  private boolean refreshedMark;
  /** Height of the rectangles. */
  private int nodeHeight = -1;
  /** Distance between the levels. */
  private int levelDistance = -1;
  /** Image of the current marked nodes. */
  private BufferedImage markedImage;
  /** If something is selected. */
  private boolean selection;
  /** The selection rectangle. */
  private ViewRect selectRect;

  /**
   * Default Constructor.
   * @param man view manager
   */
  public TreeView(final ViewNotifier man) {
    super(null, man);
    new BaseXPopup(this, GUIConstants.POPUP);
  }

  @Override
  protected void refreshContext(final boolean more, final boolean quick) {
    wwidth = -1;
    wheight = -1;
    repaint();
  }

  @Override
  protected void refreshFocus() {
    refreshedFocus = true;
    repaint();
  }

  @Override
  protected void refreshInit() {
    wwidth = -1;
    wheight = -1;
    repaint();
  }

  @Override
  protected void refreshLayout() {
    if(gui.context.data() != null) repaint();
  }

  @Override
  protected void refreshMark() {
    refreshedMark = true;
    repaint();
  }

  @Override
  protected void refreshUpdate() {
    repaint();
  }

  @Override
  public boolean visible() {
    return gui.prop.is(GUIProp.SHOWTREE);
  }

  @Override
  public void paintComponent(final Graphics g) {
    super.paintComponent(g);
    g.setFont(GUIConstants.font);

    // timer
    final Performance perf = new Performance();
    perf.initTimer();

    // initializes sizes
    fontHeight = g.getFontMetrics().getHeight();
    if(nodeHeight == -1) nodeHeight = fontHeight;
    setLevelDistance();

    if(windowSizeChanged()) {

      if(markedImage != null) {
        markedImage = null;
        refreshedMark = true;
      }

      focusedRect = null;

      treeImage = createImage();
      final Graphics tIg = treeImage.getGraphics();

      if(rectsPerLevel == null) {
        rectsPerLevel = new ArrayList<ArrayList<TreeRect>>();
      } else {
        rectsPerLevel.clear();
      }

      final Nodes curr = gui.context.current();
      for(int i = 0; i < curr.size(); i++) {
        treeView(curr.nodes[i], tIg);

      }
    }

    g.drawImage(treeImage, 0, 0, getWidth(), getHeight(), this);

    // highlights marked nodes
    if(refreshedMark) markNodes();

    if(markedImage != null) g.drawImage(markedImage, 0, 0, getWidth(),
        getHeight(), this);

    // highlights the focused node

    if(focus()) {
      highlightNode(g, markColor, focusedRect, focusedRectLevel,
          showParentNode, showChildNodes);
    }

    if(selection) markSelektedNodes(g);

  }

  /**
   * Draws the dragged selection and marks the nodes inside.
   * @param g the graphics reference
   */
  private void markSelektedNodes(final Graphics g) {
    final int x = selectRect.w < 0 ? selectRect.x + selectRect.w : selectRect.x;
    final int y = selectRect.h < 0 ? selectRect.y + selectRect.h : selectRect.y;
    final int w = Math.abs(selectRect.w);
    final int h = Math.abs(selectRect.h);

    // draw selection
    g.setColor(Color.RED);
    g.drawRect(x, y, w, h);

    final int f = y;
    final int t = y + h;
    final int size = rectsPerLevel.size();
    final IntList list = new IntList();

    for(int i = 0; i < size; i++) {
      final int yL = getYperLevel(i);

      if((yL >= f || yL + nodeHeight >= f) &&
          (yL <= t || yL + nodeHeight <= t)) {

        final ArrayList<TreeRect> rList = rectsPerLevel.get(i);

        if(rList.get(0).multiPres != null) {
          final TreeRect mRect = rList.get(0);
          final int l = mRect.multiPres.length;
          int sPrePos = (int) (l * (x / (double) mRect.w));
          int ePrePos = (int) (l * ((x + w) / (double) mRect.w));

          if(sPrePos < 0) sPrePos = 0;
          if(ePrePos >= l) ePrePos = l - 1;

          for(int j = sPrePos; j < ePrePos; j++) {
            list.add(mRect.multiPres[j]);
          }

          gui.notify.mark(new Nodes(list.finish(), gui.context.data()), this);
          markNodes();
          repaint();

        } else {

          boolean b = false;

          for(int j = 0; j < rList.size(); j++) {
            final TreeRect tR = rList.get(j);

            if(!b && tR.contains(x)) b = true;

            if(b && tR.contains(x + w)) {
              list.add(tR.pre);
              break;
            }

            if(b) {
              list.add(tR.pre);
            }

          }

          gui.notify.mark(new Nodes(list.finish(), gui.context.data()), this);
          markNodes();
          repaint();
        }

      }

    }
  }

  /**
   * Controls the node temperature drawing.
   * @param root the root node
   * @param g the graphics reference
   */
  private void treeView(final int root, final Graphics g) {
    final Data data = gui.context.data();
    int level = 0;
    sumNodeSizeInLine = data.meta.size;
    int[] parentList = { root};
    nodeCount = 1;

    while(nodeCount > 0) {
      drawNodes(g, level, parentList);
      parentList = getNextNodeLine(parentList);
      level++;
    }
    rectsPerLevel.trimToSize();
  }

  /**
   * Saves node line in parentList.
   * @param parentList array with nodes of the line before.
   * @return array filled with nodes of the current line.
   */
  private int[] getNextNodeLine(final int[] parentList) {
    final Data data = gui.context.data();
    final int l = parentList.length;
    final IntList temp = new IntList();
    int sumNodeSize = 0;
    int nCount = 0; // counts nodes
    int nSpaces = 0;

    for(int i = 0; i < l; i++) {

      final int p = parentList[i];

      if(p == -1) {
        continue;
      }

      final ChildIterator iterator = new ChildIterator(data, p);

      if(i > 0) {
        ++nSpaces;
        temp.add(-1);
      }

      while(iterator.more()) {
        final int pre = iterator.next();

        if(!onlyElementNodes) { // dead code..|| data.kind(pre) == Data.ELEM) {
          temp.add(pre);
          ++nCount;
        }
        sumNodeSize += data.size(pre, data.kind(pre));
      }
    }
    nodeSpacesPerLine = nSpaces;
    nodeCount = nCount;
    sumNodeSizeInLine = sumNodeSize;
    return temp.finish();
  }

  /**
   * Calculates the important values for drawing the nodes and invokes the
   * drawing methods.
   * @param g graphics reference
   * @param level the current level
   * @param nodeList the node list
   */
  private void drawNodes(final Graphics g, final int level,
      final int[] nodeList) {

    // calculate screen-width, if more than one root split screen in parts
    final int numberOfRoots = gui.context.current().nodes.length;
    final double screenWidth = numberOfRoots > 1 ?
        (getSize().width - 1 / (double) numberOfRoots)
        : getSize().width - 1;

        // calculate the important coordinate values of a rectangle
        final int y = getYperLevel(level);
        final int h = nodeHeight;
        final int x = 0;
        final int size = nodeList.length;
        final double w = screenWidth
        / ((double) size - (consistentNodeSpace && level > 0 ? nodeSpacesPerLine
            : 0));

        if(w < 2) {
          drawBigRectangle(g, level, nodeList, screenWidth, y, h);
        } else {
          drawRectangles(g, level, nodeList, x, w, y, h, size);
        }

  }

  /**
   * Invoked if not enough space for more than one big rectangle.
   * @param g graphics reference
   * @param level the current level
   * @param nodeList the node list
   * @param w the width
   * @param y the y coordinate
   * @param h the height
   */
  private void drawBigRectangle(final Graphics g, final int level,
      final int[] nodeList, final double w, final int y, final int h) {
    final TreeRect bigRect = new TreeRect();
    bigRect.x = 0;
    bigRect.w = (int) w;

    final IntList nodeLine = new IntList();
    for(final int pre : nodeList) {
      if(pre > -1) nodeLine.add(pre);
    }

    bigRect.multiPres = nodeLine.finish();
    final ArrayList<TreeRect> rect = new ArrayList<TreeRect>();
    rect.add(bigRect);
    rect.trimToSize();
    rectsPerLevel.add(rect);

    // draw rectangle
    g.setColor(new Color(colorRect - ((level < 11 ? level : 11) * colorDiff)));
    g.drawRect(2, y, (int) w - 1, h);

    // fill rectangle with color
    g.setColor(new Color(colorNode - ((level < 11 ? level : 11) * colorDiff)));
    g.fillRect(1, y, (int) w, h);

  }

  /**
   * Draws the rectangles.
   * @param g graphics reference
   * @param level the current level
   * @param nodeList the node list
   * @param x the x coordinate
   * @param w the width
   * @param y the y coordinate
   * @param h the height
   * @param size the size of the node list
   */
  private void drawRectangles(final Graphics g, final int level,
      final int[] nodeList, final double x, final double w, final int y,
      final int h, final int size) {

    double xx = x;

    // new array list, to be filled with the rectangles of the current level
    final ArrayList<TreeRect> rects = new ArrayList<TreeRect>();

    for(int i = 0; i < size; i++) {

      final double boxMiddle = xx + w / 2f;

      final int pre = nodeList[i];

      if(pre == -1) {

        if(!consistentNodeSpace) xx += w;

        continue;
      }

      final TreeRect rect = new TreeRect();
      rect.x = (int) xx;
      rect.w = (int) w;
      rect.pre = pre;
      rects.add(rect);

      // draw rectangle
      g.setColor(new Color(colorRect -
          ((level < 11 ? level : 11) * colorDiff)));
      g.drawRect((int) xx + 2, y, (int) w - 2, h);

      // fill rectangle with color
      g.setColor(new Color(colorNode -
          ((level < 11 ? level : 11) * colorDiff)));
      g.fillRect((int) xx + 1, y, (int) w - 1, h);

      // draw text into rectangle if enough space
      drawTextIntoRectangle(g, pre, (int) boxMiddle, (int) w, y);

      xx += w;
    }
    rects.trimToSize();
    rectsPerLevel.add(rects);
  }

  /**
   * Creates a new translucent BufferedImage.
   * @return new translucent BufferedImage
   */
  private BufferedImage createImage() {
    return new BufferedImage(Math.max(1, getWidth()), Math.max(1, getHeight()),
        Transparency.TRANSLUCENT);
  }

  /**
   * Highlights the marked nodes.
   */
  private void markNodes() {
    refreshedMark = false;
    markedImage = createImage();
    final Graphics mIg = markedImage.getGraphics();

    final int size = gui.context.marked().size();
    final int[] marked = Arrays.copyOf(gui.context.marked().sorted, size);

    for(int k = 0; k < rectsPerLevel.size(); k++) {

      final int y = getYperLevel(k);
      final ArrayList<TreeRect> rList = rectsPerLevel.get(k);

      if(rList.get(0).multiPres != null) {
        final TreeRect currRect = rList.get(0);

        for(int j = 0; j < size; j++) {
          final int pre = marked[j];

          if(pre == -1) continue;

          final int index = searchPreArrayPosition(currRect.multiPres, pre);

          if(index > -1) {

            final int x = (int) (currRect.w * index /
                (double) currRect.multiPres.length);

            mIg.setColor(Color.RED);
            mIg.drawLine(x, y, x, y + nodeHeight);
            marked[j] = -1;

          }

        }

      } else {

        for(int j = 0; j < size; j++) {
          final int pre = marked[j];

          if(pre == -1) continue;

          final TreeRect rect = searchRect(rList, pre);

          if(rect != null) {
            mIg.setColor(Color.RED);
            mIg.fillRect(rect.x + 1, y, rect.w - 1, nodeHeight);
            drawTextIntoRectangle(mIg, pre, rect.x + (int) (rect.w / 2f),
                rect.w, y);
            marked[j] = -1;
          }
        }
      }
    }
  }

  /**
   * Highlights nodes.
   * @param g the graphics reference.
   * @param c the color.
   * @param r the rectangle to highlight.
   * @param level the level.
   * @param showParent show parent.
   * @param showChildren show children.
   */
  private void highlightNode(final Graphics g, final Color c, final TreeRect r,
      final int level, final boolean showParent, final boolean showChildren) {

    if(level == -1) return;

    final int y = getYperLevel(level);
    final int h = nodeHeight;

    final Data data = gui.context.data();
    final int pre = r.pre;
    final int kind = data.kind(pre);
    final int size = data.size(pre, kind);

    g.setColor(c);
    g.drawRect(r.x, y, r.w, h);

    if(r.multiPres != null) {
      final int index = searchPreArrayPosition(r.multiPres, pre);
      final double ratio = index / (double) (r.multiPres.length - 1);
      g.drawLine((int) (r.w * ratio), y, (int) (r.w * ratio), y + nodeHeight);
    }

    if(showParent && pre > 0) {
      final int par = data.parent(pre, kind);
      final int l = level - 1;
      TreeRect parRect = null;

      if(l >= 0) {
        final ArrayList<TreeRect> rList = rectsPerLevel.get(l);

        if(rList.get(0).multiPres != null) {
          final TreeRect mPreRect = rList.get(0);
          mPreRect.pre = par;

          g.drawLine(mPreRect.w / 2, getYperLevel(l) + nodeHeight + 1,
              mPreRect.w / 2, y - 1);

          final int pIndex = searchPreArrayPosition(mPreRect.multiPres, par);
          final double pRatio = pIndex
          / (double) (mPreRect.multiPres.length - 1);

          g.setColor(Color.RED);
          g.drawLine((int) (mPreRect.w * pRatio), getYperLevel(l),
              (int) (mPreRect.w * pRatio), getYperLevel(l) + nodeHeight);

          highlightNode(g, Color.RED, mPreRect, l, true, false);

        } else {

          parRect = searchRect(rList, par);

          if(parRect != null) {
            g.drawLine((2 * parRect.x + parRect.w) / 2, getYperLevel(l)
                + nodeHeight + 1, (2 * r.x + r.w) / 2, y - 1);
            highlightNode(g, Color.RED, parRect, l, true, false);
          }
        }
      }
    }

    if(showChildren && size > 1 && level + 1 < rectsPerLevel.size()) {
      // TODO
      //
      // final int l = level + 1;
      // final ArrayList<TreeRect> rList = rectsPerLevel.get(l);
      //
      // if(rList.get(0).multiPres != null) {
      // TreeRect cRect = rList.get(0);
      //
      // final ChildIterator chIt = new ChildIterator(data, pre);
      //
      // int firstChildPre = chIt.next();
      // int lastChildPre = firstChildPre;
      //
      // cRect.pre = firstChildPre;
      // highlightNode(g, Color.WHITE, cRect, l, false, true);
      //
      // // while(chIt.more())
      // // lastChildPre = chIt.next();
      // //
      // // final int drawFrom = searchPreArrayPosition(cRect.multiPres,
      // // firstChildPre);
      // // final int drawTo = firstChildPre == lastChildPre ? drawFrom
      // // : searchPreArrayPosition(cRect.multiPres, lastChildPre);
      // //
      // // final int mPreLength = cRect.multiPres.length - 1;
      // // int cx = (int) (cRect.w * drawFrom / (double) mPreLength);
      // // int cw = (int) (cRect.w * drawTo / (double) mPreLength);
      // // g.setColor(Color.GREEN);
      // // g.fillRect(cx, getYperLevel(l), Math.max(cw - cx, 1), nodeHeight);
      // // highlightNode(g, Color.GREEN, cRect, l, false, false);
      //
      // } else {
      //
      // }

    }

    String s = "";

    if(s.length() > 0) s += " | ";

    if(kind == Data.ELEM) {
      s += Token.string(data.tag(pre));
    } else {
      s += Token.string(data.text(pre));
    }

    // for(int j = 0; j < data.attSize(pre, data.kind(pre)) - 1; j++) {
    // s += " " + Token.string(data.attName(pre + y + 1)) + "=" + "\""
    // + Token.string(data.attValue(pre + y + 1)) + "\" ";
    // }

    final int w = BaseXLayout.width(g, s);
    g.setColor(highlightColor);

    if(r.pre == 0) {

      g.fillRect(r.x, y + fontHeight + 1, w + 2, h);
      g.setColor(Color.WHITE);
      g.drawString(s, r.x + 1, y + fontHeight + h - 2);

    } else {
      g.fillRect(r.x, y - fontHeight, w + 2, h);
      g.setColor(Color.WHITE);
      g.drawString(s, r.x + 1, (int) (y - h / (float) fontHeight) - 2);

    }
  }

  /**
   * Finds rectangle at cursor position.
   * @return focused rectangle
   */
  public boolean focus() {

    if(refreshedFocus) {

      final int pre = gui.focused;

      for(int i = 0; i < rectsPerLevel.size(); i++) {
        final ArrayList<TreeRect> rectsL = rectsPerLevel.get(i);

        if(rectsL.get(0).multiPres != null) {
          final TreeRect multiPres = rectsL.get(0);
          final int index = searchPreArrayPosition(multiPres.multiPres, pre);

          if(index > -1) {
            multiPres.pre = pre;
            focusedRect = multiPres;
            focusedRectLevel = i;

            refreshedFocus = false;
            return true;
          }

        } else {

          final TreeRect rect = searchRect(rectsL, pre);

          if(rect != null) {
            focusedRect = rect;
            focusedRectLevel = i;

            refreshedFocus = false;
            return true;
          }
        }
      }
    } else {

      final int level = getLevelPerY(mousePosY);

      if(level == -1 || level >= rectsPerLevel.size()) return false;

      final Iterator<TreeRect> it = rectsPerLevel.get(level).iterator();

      while(it.hasNext()) {
        final TreeRect r = it.next();

        if(r.contains(mousePosX)) {

          focusedRect = r;
          focusedRectLevel = level;
          int pre = r.pre;

          // if multiple pre values, then approximate pre value
          if(r.multiPres != null) {
            final double ratio = mousePosX / (double) r.w;
            final int index = (int) ((r.multiPres.length - 1) * ratio);
            pre = r.multiPres[index];
            r.pre = pre;
          }

          gui.notify.focus(pre, this);

          refreshedFocus = false;
          return true;
        }

      }
    }
    refreshedFocus = false;
    return false;
  }

  /**
   * Returns the y-axis value for a given level.
   * @param level the level.
   * @return the y-axis value.
   */
  private int getYperLevel(final int level) {
    return level * nodeHeight + level * levelDistance;
  }

  /**
   * Determines the level of a y-axis value.
   * @param y the y-axis value.
   * @return the level if inside a node rectangle, -1 else.
   */
  private int getLevelPerY(final int y) {
    final double f = y / ((float) levelDistance + nodeHeight);
    final double b = nodeHeight / (float) (levelDistance + nodeHeight);
    return f <= ((int) f + b) ? (int) f : -1;
  }

  /**
   * Determines the optimal distance between the levels.
   */
  private void setLevelDistance() {
    final int levels = gui.context.data().meta.height + 1;
    final int height = getSize().height - 1;
    final int heightLeft = height - levels * nodeHeight;
    final int lD = (int) (heightLeft / (double) levels);
    levelDistance = lD < minimumLevelDistance ? minimumLevelDistance : lD;
  }

  /**
   * Draws text into the rectangles.
   * @param g graphics reference
   * @param pre the pre value.
   * @param boxMiddle the middle of the rectangle.
   * @param w the width of the.
   * @param y the y value;
   */
  private void drawTextIntoRectangle(final Graphics g, final int pre,
      final int boxMiddle, final int w, final int y) {

    if(w < minSpace) return;

    final Data data = gui.context.data();
    final int nodeKind = data.kind(pre);
    String s = "";

    switch(nodeKind) {
      case Data.ELEM:
        s = Token.string(data.tag(pre));
        g.setColor(Color.BLACK);
        break;
      case Data.COMM:
        s = Token.string(data.text(pre));
        g.setColor(Color.GREEN);
        break;
      case Data.PI:
        s = Token.string(data.text(pre));
        g.setColor(Color.PINK);
        break;
      case Data.DOC:
        s = Token.string(data.text(pre));
        g.setColor(Color.BLACK);
        break;
      default:
        s = Token.string(data.text(pre));
        g.setColor(textColor);
    }
    Token.string(data.text(pre));
    int textWidth = 0;

    while((textWidth = BaseXLayout.width(g, s)) + 4 > w) {
      s = s.substring(0, s.length() / 2).concat("*");
    }
    g.drawString(s, boxMiddle - textWidth / 2, y + nodeHeight - 2);
  }

  /**
   * Returns true if window-size has changed.
   * @return window-size has changed
   */
  private boolean windowSizeChanged() {
    if((wwidth > -1 && wheight > -1)
        && (getHeight() == wheight && getWidth() == wwidth)) return false;

    wheight = getHeight();
    wwidth = getWidth();
    return true;
  }

  /**
   * Uses binary search to find the rectangle with given pre value.
   * @param rList the rectangle list
   * @param pre the pre value to be found.
   * @return the rectangle containing the given pre value, null else.
   */
  private TreeRect searchRect(final ArrayList<TreeRect> rList, final int pre) {

    if(rList.size() == 0) return null;

    TreeRect rect = null;
    int l = 0;
    int r = rList.size() - 1;

    while(r >= l && rect == null) {
      final int m = l + (r - l) / 2;

      if(rList.get(m).pre < pre) {
        l = m + 1;
      } else if(rList.get(m).pre > pre) {
        r = m - 1;
      } else {
        rect = rList.get(m);
      }
    }
    return rect;
  }

  /**
   * Determines the index position of given pre value.
   * @param a the array to be searched
   * @param pre the pre value
   * @return the determined index position
   */
  private int searchPreArrayPosition(final int[] a, final int pre) {

    int index = -1;
    int l = 0;
    int r = a.length - 1;

    while(r >= l && index == -1) {
      final int m = l + (r - l) / 2;

      if(a[m] < pre) {
        l = m + 1;
      } else if(a[m] > pre) {
        r = m - 1;
      } else {
        index = m;
      }
    }
    return index;
  }

  @Override
  public void mouseMoved(final MouseEvent e) {
    if(gui.updating) return;
    super.mouseMoved(e);
    // refresh mouse focus
    mousePosX = e.getX();
    mousePosY = e.getY();
    focus();
    repaint();
  }

  @Override
  public void mouseClicked(final MouseEvent e) {

    final boolean left = SwingUtilities.isLeftMouseButton(e);
    final boolean right = SwingUtilities.isRightMouseButton(e);
    if(!right && !left || focusedRect == null) return;

    if(left) {
      gui.notify.mark(0, null);
      if(e.getClickCount() > 1 && focusedRect.pre > 0) {
        gui.notify.context(gui.context.marked(), false, this);
        refreshContext(false, false);
      }
    }
  }

  @Override
  public void mouseWheelMoved(final MouseWheelEvent e) {
    if(gui.updating || gui.focused == -1) return;
    if(e.getWheelRotation() > 0) gui.notify.context(new Nodes(gui.focused,
        gui.context.data()), false, null);
    else gui.notify.hist(false);
  }

  @Override
  public void mouseDragged(final MouseEvent e) {
    if(gui.updating || e.isShiftDown()) return;

    if(!selection) {
      selection = true;
      selectRect = new ViewRect();
      selectRect.x = e.getX();
      selectRect.y = e.getY();
      selectRect.h = 1;
      selectRect.w = 1;

    } else {
      final int x = e.getX();
      final int y = e.getY();

      selectRect.w = x - selectRect.x;
      selectRect.h = y - selectRect.y;

    }
    repaint();
  }

  @Override
  public void mouseReleased(final MouseEvent e) {
    if(gui.updating || gui.painting) return;
    selection = false;
    repaint();
  }
}
