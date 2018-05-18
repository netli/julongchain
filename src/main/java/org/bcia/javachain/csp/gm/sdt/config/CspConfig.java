/**
 * Copyright SDT. All Rights Reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bcia.javachain.csp.gm.sdt.config;

/**
 * CSP配置信息
 *
 * @author tengxiumin
 * @date 2018/05/17
 * @company SDT
 */
public class CspConfig {

    private String defaultValue;
    private SdtgmConfig sdtgm;

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public SdtgmConfig getSdtgm() {
        return sdtgm;
    }

    public void setSdtgm(SdtgmConfig sdtgm) {
        this.sdtgm = sdtgm;
    }
}
