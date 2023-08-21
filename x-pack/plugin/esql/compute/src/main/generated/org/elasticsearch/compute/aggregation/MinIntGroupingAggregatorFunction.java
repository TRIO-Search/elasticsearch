// Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
// or more contributor license agreements. Licensed under the Elastic License
// 2.0; you may not use this file except in compliance with the Elastic License
// 2.0.
package org.elasticsearch.compute.aggregation;

import java.lang.Integer;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.util.List;
import org.elasticsearch.common.util.BigArrays;
import org.elasticsearch.compute.data.Block;
import org.elasticsearch.compute.data.BooleanBlock;
import org.elasticsearch.compute.data.BooleanVector;
import org.elasticsearch.compute.data.ElementType;
import org.elasticsearch.compute.data.IntBlock;
import org.elasticsearch.compute.data.IntVector;
import org.elasticsearch.compute.data.LongBlock;
import org.elasticsearch.compute.data.LongVector;
import org.elasticsearch.compute.data.Page;

/**
 * {@link GroupingAggregatorFunction} implementation for {@link MinIntAggregator}.
 * This class is generated. Do not edit it.
 */
public final class MinIntGroupingAggregatorFunction implements GroupingAggregatorFunction {
  private static final List<IntermediateStateDesc> INTERMEDIATE_STATE_DESC = List.of(
      new IntermediateStateDesc("min", ElementType.INT),
      new IntermediateStateDesc("seen", ElementType.BOOLEAN)  );

  private final IntArrayState state;

  private final List<Integer> channels;

  public MinIntGroupingAggregatorFunction(List<Integer> channels, IntArrayState state) {
    this.channels = channels;
    this.state = state;
  }

  public static MinIntGroupingAggregatorFunction create(List<Integer> channels,
      BigArrays bigArrays) {
    return new MinIntGroupingAggregatorFunction(channels, new IntArrayState(bigArrays, MinIntAggregator.init()));
  }

  public static List<IntermediateStateDesc> intermediateStateDesc() {
    return INTERMEDIATE_STATE_DESC;
  }

  @Override
  public int intermediateBlockCount() {
    return INTERMEDIATE_STATE_DESC.size();
  }

  @Override
  public GroupingAggregatorFunction.AddInput prepareProcessPage(SeenGroupIds seenGroupIds,
      Page page) {
    Block uncastValuesBlock = page.getBlock(channels.get(0));
    if (uncastValuesBlock.areAllValuesNull()) {
      state.enableGroupIdTracking(seenGroupIds);
      return new GroupingAggregatorFunction.AddInput() {
        @Override
        public void add(int positionOffset, LongBlock groupIds) {
        }

        @Override
        public void add(int positionOffset, LongVector groupIds) {
        }
      };
    }
    IntBlock valuesBlock = (IntBlock) uncastValuesBlock;
    IntVector valuesVector = valuesBlock.asVector();
    if (valuesVector == null) {
      if (valuesBlock.mayHaveNulls()) {
        state.enableGroupIdTracking(seenGroupIds);
      }
      return new GroupingAggregatorFunction.AddInput() {
        @Override
        public void add(int positionOffset, LongBlock groupIds) {
          addRawInput(positionOffset, groupIds, valuesBlock);
        }

        @Override
        public void add(int positionOffset, LongVector groupIds) {
          addRawInput(positionOffset, groupIds, valuesBlock);
        }
      };
    }
    return new GroupingAggregatorFunction.AddInput() {
      @Override
      public void add(int positionOffset, LongBlock groupIds) {
        addRawInput(positionOffset, groupIds, valuesVector);
      }

      @Override
      public void add(int positionOffset, LongVector groupIds) {
        addRawInput(positionOffset, groupIds, valuesVector);
      }
    };
  }

  private void addRawInput(int positionOffset, LongVector groups, IntBlock values) {
    for (int groupPosition = 0; groupPosition < groups.getPositionCount(); groupPosition++) {
      int groupId = Math.toIntExact(groups.getLong(groupPosition));
      if (values.isNull(groupPosition + positionOffset)) {
        continue;
      }
      int valuesStart = values.getFirstValueIndex(groupPosition + positionOffset);
      int valuesEnd = valuesStart + values.getValueCount(groupPosition + positionOffset);
      for (int v = valuesStart; v < valuesEnd; v++) {
        state.set(groupId, MinIntAggregator.combine(state.getOrDefault(groupId), values.getInt(v)));
      }
    }
  }

  private void addRawInput(int positionOffset, LongVector groups, IntVector values) {
    for (int groupPosition = 0; groupPosition < groups.getPositionCount(); groupPosition++) {
      int groupId = Math.toIntExact(groups.getLong(groupPosition));
      state.set(groupId, MinIntAggregator.combine(state.getOrDefault(groupId), values.getInt(groupPosition + positionOffset)));
    }
  }

  private void addRawInput(int positionOffset, LongBlock groups, IntBlock values) {
    for (int groupPosition = 0; groupPosition < groups.getPositionCount(); groupPosition++) {
      if (groups.isNull(groupPosition)) {
        continue;
      }
      int groupStart = groups.getFirstValueIndex(groupPosition);
      int groupEnd = groupStart + groups.getValueCount(groupPosition);
      for (int g = groupStart; g < groupEnd; g++) {
        int groupId = Math.toIntExact(groups.getLong(g));
        if (values.isNull(groupPosition + positionOffset)) {
          continue;
        }
        int valuesStart = values.getFirstValueIndex(groupPosition + positionOffset);
        int valuesEnd = valuesStart + values.getValueCount(groupPosition + positionOffset);
        for (int v = valuesStart; v < valuesEnd; v++) {
          state.set(groupId, MinIntAggregator.combine(state.getOrDefault(groupId), values.getInt(v)));
        }
      }
    }
  }

  private void addRawInput(int positionOffset, LongBlock groups, IntVector values) {
    for (int groupPosition = 0; groupPosition < groups.getPositionCount(); groupPosition++) {
      if (groups.isNull(groupPosition)) {
        continue;
      }
      int groupStart = groups.getFirstValueIndex(groupPosition);
      int groupEnd = groupStart + groups.getValueCount(groupPosition);
      for (int g = groupStart; g < groupEnd; g++) {
        int groupId = Math.toIntExact(groups.getLong(g));
        state.set(groupId, MinIntAggregator.combine(state.getOrDefault(groupId), values.getInt(groupPosition + positionOffset)));
      }
    }
  }

  @Override
  public void addIntermediateInput(int positionOffset, LongVector groups, Page page) {
    state.enableGroupIdTracking(new SeenGroupIds.Empty());
    assert channels.size() == intermediateBlockCount();
    IntVector min = page.<IntBlock>getBlock(channels.get(0)).asVector();
    BooleanVector seen = page.<BooleanBlock>getBlock(channels.get(1)).asVector();
    assert min.getPositionCount() == seen.getPositionCount();
    for (int groupPosition = 0; groupPosition < groups.getPositionCount(); groupPosition++) {
      int groupId = Math.toIntExact(groups.getLong(groupPosition));
      if (seen.getBoolean(groupPosition + positionOffset)) {
        state.set(groupId, MinIntAggregator.combine(state.getOrDefault(groupId), min.getInt(groupPosition + positionOffset)));
      }
    }
  }

  @Override
  public void addIntermediateRowInput(int groupId, GroupingAggregatorFunction input, int position) {
    if (input.getClass() != getClass()) {
      throw new IllegalArgumentException("expected " + getClass() + "; got " + input.getClass());
    }
    IntArrayState inState = ((MinIntGroupingAggregatorFunction) input).state;
    state.enableGroupIdTracking(new SeenGroupIds.Empty());
    if (inState.hasValue(position)) {
      state.set(groupId, MinIntAggregator.combine(state.getOrDefault(groupId), inState.get(position)));
    }
  }

  @Override
  public void evaluateIntermediate(Block[] blocks, int offset, IntVector selected) {
    state.toIntermediate(blocks, offset, selected);
  }

  @Override
  public void evaluateFinal(Block[] blocks, int offset, IntVector selected) {
    blocks[offset] = state.toValuesBlock(selected);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getClass().getSimpleName()).append("[");
    sb.append("channels=").append(channels);
    sb.append("]");
    return sb.toString();
  }

  @Override
  public void close() {
    state.close();
  }
}