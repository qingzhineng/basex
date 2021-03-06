package org.basex.query.expr.index;

import static org.basex.query.QueryText.*;

import org.basex.data.*;
import org.basex.index.*;
import org.basex.index.query.*;
import org.basex.query.*;
import org.basex.query.expr.*;
import org.basex.query.func.*;
import org.basex.query.iter.*;
import org.basex.query.util.*;
import org.basex.query.value.item.*;
import org.basex.query.value.node.*;
import org.basex.query.var.*;
import org.basex.util.*;
import org.basex.util.hash.*;

/**
 * This index class retrieves ranges from a value index.
 *
 * @author BaseX Team 2005-16, BSD License
 * @author Christian Gruen
 */
public final class StringRangeAccess extends IndexAccess {
  /** Index token. */
  private final StringRange index;

  /**
   * Constructor.
   * @param info input info
   * @param index index reference
   * @param ictx index context
   */
  public StringRangeAccess(final InputInfo info, final StringRange index, final IndexContext ictx) {
    super(ictx, info);
    this.index = index;
  }

  @Override
  public BasicNodeIter iter(final QueryContext qc) {
    final boolean text = index.type() == IndexType.TEXT;
    final byte kind = text ? Data.TEXT : Data.ATTR;
    final Data data = ictx.data;
    final int ml = data.meta.maxlen;
    final IndexIterator ii = index.min.length <= ml && index.max.length <= ml &&
        (text ? data.meta.textindex : data.meta.attrindex) ? data.iter(index) : scan();

    return new DBNodeIter(data) {
      @Override
      public DBNode next() {
        return ii.more() ? new DBNode(data, ii.pre(), kind) : null;
      }
    };
  }

  /**
   * Returns scan-based iterator.
   * @return node iterator
   */
  private IndexIterator scan() {
    return new IndexIterator() {
      final boolean text = index.type() == IndexType.TEXT;
      final byte kind = text ? Data.TEXT : Data.ATTR;
      final Data data = ictx.data;
      final int sz = data.meta.size;
      int pre = -1;

      @Override
      public int pre() {
        return pre;
      }
      @Override
      public boolean more() {
        while(++pre < sz) {
          if(data.kind(pre) != kind) continue;
          final byte[] t = data.text(pre, text);
          final int mn = Token.diff(t, index.min);
          final int mx = Token.diff(t, index.max);
          if(mn >= (index.mni ? 0 : 1) && mx <= (index.mxi ? 0 : 1)) return true;
        }
        return false;
      }
      @Override
      public int size() {
        return Math.max(1, sz >>> 2);
      }
    };
  }

  @Override
  public Expr copy(final CompileContext cc, final IntObjMap<Var> vm) {
    return new StringRangeAccess(info, index, ictx);
  }

  @Override
  public void plan(final FElem plan) {
    addPlan(plan, planElem(DATA, ictx.data.meta.name,
        MIN, index.min, MAX, index.max, TYP, index.type()));
  }

  @Override
  public String toString() {
    final boolean text = index.type() == IndexType.TEXT;
    final Function func = text ? Function._DB_TEXT_RANGE : Function._DB_ATTRIBUTE_RANGE;
    return func.toString(Str.get(ictx.data.meta.name), Str.get(index.min), Str.get(index.max));
  }
}
