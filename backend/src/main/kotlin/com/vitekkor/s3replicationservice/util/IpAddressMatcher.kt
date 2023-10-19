package com.vitekkor.s3replicationservice.util

import org.springframework.util.StringUtils
import java.net.InetAddress
import java.net.UnknownHostException

class IpAddressMatcher(ipAddress: String) {
    private val nMaskBits: Int

    private val requiredAddress: InetAddress

    init {
        var address = ipAddress
        if (address.indexOf('/') > 0) {
            val addressAndMask = StringUtils.split(address, "/")
            address = addressAndMask!![0]
            nMaskBits = addressAndMask[1].toInt()
        } else {
            nMaskBits = -1
        }
        requiredAddress = parseAddress(address)!!
        require(requiredAddress.address.size * 8 >= nMaskBits) {
            "IP address $address is too short for bitmask of length $nMaskBits"
        }
    }

    fun matches(address: String): Boolean {
        val remoteAddress = parseAddress(address) ?: return false
        if (requiredAddress.javaClass != remoteAddress.javaClass) {
            return false
        }
        if (nMaskBits < 0) {
            return remoteAddress == requiredAddress
        }
        val remAddr = remoteAddress.address
        val reqAddr = requiredAddress.address
        val nMaskFullBytes = nMaskBits / 8
        val finalByte = (0xFF00 shr (nMaskBits and 0x07)).toByte()
        for (i in 0 until nMaskFullBytes) {
            if (remAddr[i] != reqAddr[i]) {
                return false
            }
        }
        return if (finalByte.toInt() != 0) {
            remAddr[nMaskFullBytes].toInt() and finalByte.toInt() == reqAddr[nMaskFullBytes].toInt() and finalByte.toInt()
        } else true
    }

    private fun parseAddress(address: String): InetAddress? {
        return try {
            InetAddress.getByName(address)
        } catch (ex: UnknownHostException) {
            return null
        }
    }
}
