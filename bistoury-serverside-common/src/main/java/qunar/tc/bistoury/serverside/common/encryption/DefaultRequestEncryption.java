/*
 * Copyright (C) 2019 Qunar, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package qunar.tc.bistoury.serverside.common.encryption;

import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qunar.tc.bistoury.common.JacksonSerializer;
import qunar.tc.bistoury.remoting.protocol.RequestData;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author zhenyu.nie created on 2019 2019/5/16 15:53
 */
public class DefaultRequestEncryption implements RequestEncryption {

    private static Logger logger = LoggerFactory.getLogger(DefaultRequestEncryption.class);

    private static final TypeReference<Map<String, Object>> mapReference = new TypeReference<Map<String, Object>>() {
    };

    private static final TypeReference<RequestData<String>> inputType = new TypeReference<RequestData<String>>() {
    };

    private static final String KEY_INDEX = "0"; // key字段
    private static final String DATA_INDEX = "1";// value字段

    private final RSAEncryption rsa;

    public DefaultRequestEncryption(RSAEncryption rsa) {
        this.rsa = rsa;
    }

    @Override
    public RequestData<String> decrypt(String in) throws IOException {
        Map<String, Object> map = JacksonSerializer.deSerialize(in, mapReference); // 解析为map： {"0":"rWyLwiZBc4ew63Ck79Sr0crZ3P5YRuyFZgnMfJA0esg3ZjeR2zLAFYQUIcF+26c55e1ATc3q6qSDhwXrLE2IEIyrJdMA0iBFQ8gXJmtJ7Y8U7ZsdavGqH1Nyf+XCJxcpgHjO1sQiEXkrmCtqJifaoncehJV+p5vIjDaAOTn8iHk=","1":"Sz4A8LYtvz4y7zSHqd2TXuELgu/bC+dVErh+NCGB74YbMaV7o1ZhF3ork+SjOXetWhzBBNwXIXw8Oso+AyHR28q6/KkBSH1vUVsSTgXfvwlOqFy2X5vLyhk0zXMh+l9gFRI3aEcNXE6M3sVGYHA4y9J76d8wgA0MRgBpXmTXXYPC/DTsqL7Myw=="}
        String rsaData = (String) map.get(KEY_INDEX); // key=rWyLwiZBc4ew63Ck79Sr0crZ3P5YRuyFZgnMfJA0esg3ZjeR2zLAFYQUIcF+26c55e1ATc3q6qSDhwXrLE2IEIyrJdMA0iBFQ8gXJmtJ7Y8U7ZsdavGqH1Nyf+XCJxcpgHjO1sQiEXkrmCtqJifaoncehJV+p5vIjDaAOTn8iHk=
        String data = (String) map.get(DATA_INDEX); // value=Sz4A8LYtvz4y7zSHqd2TXuELgu/bC+dVErh+NCGB74YbMaV7o1ZhF3ork+SjOXetWhzBBNwXIXw8Oso+AyHR28q6/KkBSH1vUVsSTgXfvwlOqFy2X5vLyhk0zXMh+l9gFRI3aEcNXE6M3sVGYHA4y9J76d8wgA0MRgBpXmTXXYPC/DTsqL7Myw==

        String desKey = rsa.decrypt(rsaData);
        String requestStr = EncryptionUtils.decryptDes(data, desKey); // {"user":"admin","type":10,"app":"bistoury_demo_app","hosts":["10.2.40.18"],"command":"","token":"5a9f3556960290d9a62fdba1a1255b9b"}
        logger.info("decrypt, map={}, rsaData={}, data={}, desKey={}, requestStr={}",map,rsaData,desKey,requestStr);
        return JacksonSerializer.deSerialize(requestStr, inputType);
    }

    @Override
    public String encrypt(RequestData<String> requestData, final String key) throws IOException {
        Map<String, String> map = new HashMap<>();
        String encrypt = rsa.encrypt(key);
        map.put(KEY_INDEX, encrypt);

        String encryptDes = EncryptionUtils.encryptDes(JacksonSerializer.serialize(requestData), key);
        map.put(DATA_INDEX, encryptDes);
        return JacksonSerializer.serialize(map);
    }


}
