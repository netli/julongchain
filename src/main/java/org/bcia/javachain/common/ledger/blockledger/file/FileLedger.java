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
package org.bcia.javachain.common.ledger.blockledger.file;

import org.bcia.javachain.common.ledger.blockledger.FileLedgerBlockStore;
import org.bcia.javachain.common.ledger.blockledger.Iterator;
import org.bcia.javachain.common.ledger.blockledger.Reader;
import org.bcia.javachain.common.ledger.blockledger.Writer;
import org.bcia.javachain.common.log.JavaChainLog;
import org.bcia.javachain.common.log.JavaChainLogFactory;
import org.bcia.javachain.protos.common.Common;
import org.bcia.javachain.protos.consenter.Ab;

/**
 * 文件账本
 *
 * @author sunzongyu
 * @date 2018/04/26
 * @company Dingxuan
 */
public class FileLedger implements Reader, Writer {
    private static final JavaChainLog logger = JavaChainLogFactory.getLog(FileLedger.class);

    private FileLedgerBlockStore blockStore;

    public void init(){

    }

    @Override
    public Iterator iterator(Ab.SeekPosition startType) {

        return null;
    }

    @Override
    public long height() {
        return 0;
    }

    @Override
    public void append(Common.Block block) {

    }

    public FileLedgerBlockStore getBlockStore() {
        return blockStore;
    }

    public void setBlockStore(FileLedgerBlockStore blockStore) {
        this.blockStore = blockStore;
    }
}
