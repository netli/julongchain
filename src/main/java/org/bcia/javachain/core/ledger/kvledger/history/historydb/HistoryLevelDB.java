/*
 * Copyright Dingxuan. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

		 http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package org.bcia.javachain.core.ledger.kvledger.history.historydb;

import com.google.protobuf.ByteString;
import org.bcia.javachain.common.exception.LedgerException;
import org.bcia.javachain.common.ledger.blkstorage.IBlockStore;
import org.bcia.javachain.common.ledger.util.IDBProvider;
import org.bcia.javachain.common.ledger.util.leveldbhelper.LevelDBProvider;
import org.bcia.javachain.common.ledger.util.leveldbhelper.UpdateBatch;
import org.bcia.javachain.common.log.JavaChainLog;
import org.bcia.javachain.common.log.JavaChainLogFactory;
import org.bcia.javachain.core.ledger.BlockAndPvtData;
import org.bcia.javachain.core.ledger.kvledger.KvLedger;
import org.bcia.javachain.core.ledger.kvledger.history.IHistoryQueryExecutor;
import org.bcia.javachain.core.ledger.ledgerconfig.LedgerConfig;
import org.bcia.javachain.core.ledger.util.TxValidationFlags;
import org.bcia.javachain.core.ledger.util.Util;
import org.bcia.javachain.core.ledger.kvledger.txmgmt.rwsetutil.NsRwSet;
import org.bcia.javachain.core.ledger.kvledger.txmgmt.rwsetutil.TxRwSet;
import org.bcia.javachain.core.ledger.kvledger.txmgmt.version.Height;
import org.bcia.javachain.protos.common.Common;
import org.bcia.javachain.protos.ledger.rwset.kvrwset.KvRwset;
import org.bcia.javachain.protos.node.ProposalPackage;

import java.util.List;

/**
 * LevelDB实现的HistoryDB
 *
 * @author sunzongyu
 * @date 2018/04/04
 * @company Dingxuan
 */
public class HistoryLevelDB implements IHistoryDB {

    private static final JavaChainLog logger = JavaChainLogFactory.getLog(HistoryLevelDB.class);
    private IDBProvider provider = null;
    private String dbName = null;

    private static final byte[] EMPTY_VALUE = {};
    private static final byte[] SAVE_POINT_KEY = {0x00};

    public HistoryLevelDB(IDBProvider dbProvider, String dbName) {
        this.dbName = dbName;
        this.provider = dbProvider;
    }

    @Override
    public IHistoryQueryExecutor newHistoryQueryExecutor(IBlockStore blockStore) {
        return new HistoryLevelDBQueryExecutor(this, blockStore);
    }

    @Override
    public void commit(Common.Block block) throws LedgerException {
        long blockNo = block.getHeader().getNumber();
        int tranNo = 0;
        UpdateBatch dbBatch = new UpdateBatch();
        logger.debug(String.format("Group [%s]: Updating historyDB for groupNo [%s] with [%d] transactions"
                , dbName, blockNo, block.getData().getDataCount()));
        //获取失效
        TxValidationFlags txsFilter = TxValidationFlags.fromByteString(block.getMetadata().getMetadata(Common.BlockMetadataIndex.TRANSACTIONS_FILTER.getNumber()));
        //没有失效的部分
        if(txsFilter.length() == 0){
            txsFilter = new TxValidationFlags(block.getData().getDataCount());
            block = block.toBuilder()
                    .setMetadata(block.getMetadata().toBuilder()
                            .setMetadata(Common.BlockMetadataIndex.TRANSACTIONS_FILTER.getNumber(), txsFilter.toByteString())
                            .build())
                    .build();
        }
        //将每个交易的写集写入HistoryDB
        List<ByteString> list = block.getData().getDataList();
        for (; tranNo < list.size(); tranNo++) {
            ByteString evnByte = list.get(tranNo);
            if(txsFilter.isInValid(tranNo)){
                logger.debug(String.format("Group [%s]: Skipping write into historyDB for invalid transaction number %d."
                        , dbName, tranNo));
                continue;
            }
            Common.Envelope env = Util.getEnvelopFromBlock(evnByte);
            Common.Payload payload = Util.getPayload(env);
            Common.GroupHeader header = Util.getGroupHeader(payload.getHeader().getGroupHeader());
            //经过背书的交易写入HistoryDB
            if(Common.HeaderType.ENDORSER_TRANSACTION.getNumber() == header.getType()){
                ProposalPackage.SmartContractAction respPayload = null;
                TxRwSet txRWSet = new TxRwSet();
                respPayload = Util.getActionFromEnvelope(evnByte);
                if(respPayload == null || !respPayload.hasResponse()){
                    logger.debug("Got null respPayload from env");
                    continue;
                }
                txRWSet.fromProtoBytes(respPayload.getResults());
                for(NsRwSet nsRwSet : txRWSet.getNsRwSets()){
                    String ns = nsRwSet.getNameSpace();
                    for(KvRwset.KVWrite kvWrite : nsRwSet.getKvRwSet().getWritesList()){
                        String writeKey = kvWrite.getKey();
                        //key:ns~key~blockNo~tranNo
                        byte[] compositeHistoryKey = HistoryDBHelper.constructCompositeHistoryKey(ns, writeKey, blockNo, tranNo);
                        dbBatch.put(compositeHistoryKey, EMPTY_VALUE);
                    }
                }
            } else {
                logger.debug(String.format("Group [%s]: Skipping transaction [%d] since it is not an endorsement transaction"
                        , dbName, tranNo));
            }
        }

        //添加保存点
        Height height = new Height(blockNo,tranNo);
        dbBatch.put(SAVE_POINT_KEY, height.toBytes());

        //同步写入leveldb
        provider.writeBatch(dbBatch, true);

        logger.debug(String.format("Group [%s]: Update committed to historydb for blockNo [%d]"
                ,dbName, blockNo));
    }

    @Override
    public Height getLastSavepoint() throws LedgerException {
        byte[] versionBytes = provider.get(SAVE_POINT_KEY);
        if(versionBytes == null){
            return null;
        }
        Height height = new Height(versionBytes);
        return height;
    }

    /**
     * @return 返回应添加的blocknumber, 0为未配置HistoryDB, -1为保存点为空(既需要恢复)
     */
    @Override
    public long shouldRecover() throws LedgerException {
        if(!LedgerConfig.isHistoryDBEnabled()){
            return 0;
        }
        Height savePoint = getLastSavepoint();
        if(savePoint == null){
            return -1;
        }
        return savePoint.getBlockNum() + 1;
    }

    @Override
    public long recoverPoint(long lastAvailableBlock) throws LedgerException {
        if(!LedgerConfig.isHistoryDBEnabled()){
            return 0;
        }
        Height savePoint = getLastSavepoint();
        if(savePoint == null){
            return 0;
        }
        return savePoint.getBlockNum() + 1;
    }

    @Override
    public void commitLostBlock(BlockAndPvtData blockAndPvtData) throws LedgerException {
        commit(blockAndPvtData.getBlock());
    }

    /**
     * 判断交易是否为有效交易
     */
    private boolean isInvalid(ByteString tx){

        return true;
    }

    public IDBProvider getProvider() {
        return provider;
    }

    public void setProvider(IDBProvider provider) {
        this.provider = provider;
    }

    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }
}
