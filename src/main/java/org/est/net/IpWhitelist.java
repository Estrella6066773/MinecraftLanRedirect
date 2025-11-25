package org.est.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class IpWhitelist {
    private static final Logger LOGGER = LoggerFactory.getLogger(IpWhitelist.class);
    private final List<Subnet> subnets;

    private IpWhitelist(List<Subnet> subnets) {
        this.subnets = Collections.unmodifiableList(new ArrayList<Subnet>(subnets));
    }

    public static IpWhitelist from(List<String> cidrBlocks) {
        if (cidrBlocks == null || cidrBlocks.isEmpty()) {
            return new IpWhitelist(Collections.<Subnet>emptyList());
        }
        List<Subnet> subnets = new ArrayList<>();
        for (String cidr : cidrBlocks) {
            if (cidr == null || cidr.isBlank()) {
                continue;
            }
            String normalized = cidr.trim().toLowerCase(Locale.ROOT);
            if ("any".equals(normalized) || "*".equals(normalized)) {
                return new IpWhitelist(Collections.<Subnet>emptyList());
            }
            try {
                subnets.add(Subnet.parse(normalized));
            } catch (Exception ex) {
                LOGGER.warn("无法解析白名单条目: {}", cidr, ex);
            }
        }
        return new IpWhitelist(subnets);
    }

    public boolean isAllowed(InetAddress address) {
        Objects.requireNonNull(address, "address");
        if (subnets.isEmpty()) {
            return true;
        }
        for (Subnet subnet : subnets) {
            if (subnet.matches(address)) {
                return true;
            }
        }
        return false;
    }

    private static final class Subnet {
        private final byte[] baseAddress;
        private final byte[] mask;

        private Subnet(byte[] baseAddress, byte[] mask) {
            this.baseAddress = baseAddress;
            this.mask = mask;
        }

        static Subnet parse(String cidr) throws UnknownHostException {
            String[] parts = cidr.split("/");
            if (parts.length != 2) {
                throw new IllegalArgumentException("CIDR格式错误: " + cidr);
            }
            InetAddress base = InetAddress.getByName(parts[0]);
            int prefix = Integer.parseInt(parts[1]);
            byte[] addressBytes = base.getAddress();
            byte[] mask = buildMask(addressBytes.length, prefix);
            return new Subnet(addressBytes, mask);
        }

        boolean matches(InetAddress address) {
            byte[] addr = address.getAddress();
            if (addr.length != baseAddress.length) {
                return false;
            }
            for (int i = 0; i < addr.length; i++) {
                if ((addr[i] & mask[i]) != (baseAddress[i] & mask[i])) {
                    return false;
                }
            }
            return true;
        }

        private static byte[] buildMask(int length, int prefix) {
            if (prefix < 0 || prefix > length * 8) {
                throw new IllegalArgumentException("前缀超出范围: " + prefix);
            }
            byte[] mask = new byte[length];
            int fullBytes = prefix / 8;
            int remainder = prefix % 8;
            for (int i = 0; i < fullBytes; i++) {
                mask[i] = (byte) 0xFF;
            }
            if (remainder > 0 && fullBytes < mask.length) {
                mask[fullBytes] = (byte) (0xFF << (8 - remainder));
            }
            return mask;
        }
    }
}

