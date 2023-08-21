/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.compute.aggregation;

import org.elasticsearch.common.util.BigArrays;
import org.elasticsearch.compute.data.Block;
import org.elasticsearch.compute.data.BooleanBlock;
import org.elasticsearch.compute.data.BooleanVector;
import org.elasticsearch.compute.data.ElementType;
import org.elasticsearch.compute.data.IntVector;
import org.elasticsearch.compute.data.LongBlock;
import org.elasticsearch.compute.data.LongVector;
import org.elasticsearch.compute.data.Page;
import org.elasticsearch.compute.data.Vector;

import java.util.List;

public class CountGroupingAggregatorFunction implements GroupingAggregatorFunction {

    private static final List<IntermediateStateDesc> INTERMEDIATE_STATE_DESC = List.of(
        new IntermediateStateDesc("count", ElementType.LONG),
        new IntermediateStateDesc("seen", ElementType.BOOLEAN)
    );

    private final LongArrayState state;
    private final List<Integer> channels;

    public static CountGroupingAggregatorFunction create(BigArrays bigArrays, List<Integer> inputChannels) {
        return new CountGroupingAggregatorFunction(inputChannels, new LongArrayState(bigArrays, 0));
    }

    public static List<IntermediateStateDesc> intermediateStateDesc() {
        return INTERMEDIATE_STATE_DESC;
    }

    private CountGroupingAggregatorFunction(List<Integer> channels, LongArrayState state) {
        this.channels = channels;
        this.state = state;
    }

    @Override
    public int intermediateBlockCount() {
        return intermediateStateDesc().size();
    }

    @Override
    public AddInput prepareProcessPage(SeenGroupIds seenGroupIds, Page page) {
        Block valuesBlock = page.getBlock(channels.get(0));
        if (valuesBlock.areAllValuesNull()) {
            state.enableGroupIdTracking(seenGroupIds);
            return new AddInput() { // TODO return null meaning "don't collect me" and skip those
                @Override
                public void add(int positionOffset, LongBlock groupIds) {}

                @Override
                public void add(int positionOffset, LongVector groupIds) {}
            };
        }
        Vector valuesVector = valuesBlock.asVector();
        if (valuesVector == null) {
            if (valuesBlock.mayHaveNulls()) {
                state.enableGroupIdTracking(seenGroupIds);
            }
            return new AddInput() {
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
        return new AddInput() {
            @Override
            public void add(int positionOffset, LongBlock groupIds) {
                addRawInput(groupIds);
            }

            @Override
            public void add(int positionOffset, LongVector groupIds) {
                addRawInput(groupIds);
            }
        };
    }

    private void addRawInput(int positionOffset, LongVector groups, Block values) {
        int position = positionOffset;
        for (int groupPosition = 0; groupPosition < groups.getPositionCount(); groupPosition++, position++) {
            int groupId = Math.toIntExact(groups.getLong(groupPosition));
            if (values.isNull(position)) {
                continue;
            }
            state.increment(groupId, values.getValueCount(position));
        }
    }

    private void addRawInput(int positionOffset, LongBlock groups, Block values) {
        int position = positionOffset;
        for (int groupPosition = 0; groupPosition < groups.getPositionCount(); groupPosition++, position++) {
            if (groups.isNull(groupPosition)) {
                continue;
            }
            int groupStart = groups.getFirstValueIndex(groupPosition);
            int groupEnd = groupStart + groups.getValueCount(groupPosition);
            for (int g = groupStart; g < groupEnd; g++) {
                int groupId = Math.toIntExact(groups.getLong(g));
                if (values.isNull(position)) {
                    continue;
                }
                state.increment(groupId, values.getValueCount(position));
            }
        }
    }

    private void addRawInput(LongVector groups) {
        for (int groupPosition = 0; groupPosition < groups.getPositionCount(); groupPosition++) {
            int groupId = Math.toIntExact(groups.getLong(groupPosition));
            state.increment(groupId, 1);
        }
    }

    private void addRawInput(LongBlock groups) {
        for (int groupPosition = 0; groupPosition < groups.getPositionCount(); groupPosition++) {
            if (groups.isNull(groupPosition)) {
                continue;
            }
            int groupStart = groups.getFirstValueIndex(groupPosition);
            int groupEnd = groupStart + groups.getValueCount(groupPosition);
            for (int g = groupStart; g < groupEnd; g++) {
                int groupId = Math.toIntExact(groups.getLong(g));
                state.increment(groupId, 1);
            }
        }
    }

    @Override
    public void addIntermediateInput(int positionOffset, LongVector groups, Page page) {
        assert channels.size() == intermediateBlockCount();
        assert page.getBlockCount() >= channels.get(0) + intermediateStateDesc().size();
        state.enableGroupIdTracking(new SeenGroupIds.Empty());
        LongVector count = page.<LongBlock>getBlock(channels.get(0)).asVector();
        BooleanVector seen = page.<BooleanBlock>getBlock(channels.get(1)).asVector();
        assert count.getPositionCount() == seen.getPositionCount();
        for (int groupPosition = 0; groupPosition < groups.getPositionCount(); groupPosition++) {
            state.increment(Math.toIntExact(groups.getLong(groupPosition)), count.getLong(groupPosition + positionOffset));
        }
    }

    @Override
    public void addIntermediateRowInput(int groupId, GroupingAggregatorFunction input, int position) {
        if (input.getClass() != getClass()) {
            throw new IllegalArgumentException("expected " + getClass() + "; got " + input.getClass());
        }
        final LongArrayState inState = ((CountGroupingAggregatorFunction) input).state;
        state.enableGroupIdTracking(new SeenGroupIds.Empty());
        if (inState.hasValue(position)) {
            state.increment(groupId, inState.get(position));
        }
    }

    @Override
    public void evaluateIntermediate(Block[] blocks, int offset, IntVector selected) {
        state.toIntermediate(blocks, offset, selected);
    }

    @Override
    public void evaluateFinal(Block[] blocks, int offset, IntVector selected) {
        LongVector.Builder builder = LongVector.newVectorBuilder(selected.getPositionCount());
        for (int i = 0; i < selected.getPositionCount(); i++) {
            int si = selected.getInt(i);
            builder.appendLong(state.hasValue(si) ? state.get(si) : 0);
        }
        blocks[offset] = builder.build().asBlock();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getSimpleName()).append("[");
        sb.append("channels=").append(channels);
        sb.append("]");
        return sb.toString();
    }

    @Override
    public void close() {
        state.close();
    }
}