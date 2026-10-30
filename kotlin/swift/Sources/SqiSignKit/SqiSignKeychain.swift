import Foundation
import Security

/// Stores SQIsign secret keys as generic-password Keychain items: one item per `account` of `service`.
///
/// The system encrypts Keychain items at rest. `store(_:account:)` hands the key to the Security framework from a
/// buffer that is wiped afterwards; `secretKey(_:account:)` receives it in a `CFData` that cannot be wiped. Signing
/// still needs the key in process memory: the Secure Enclave cannot run SQIsign.
///
/// Keychain failures throw an `NSError` in `NSOSStatusErrorDomain` with the `OSStatus` as its code.
public struct SqiSignKeychain {
    /// The `kSecAttrService` of the items.
    public let service: String
    /// The `kSecAttrAccessible` class of stored items.
    public let accessibility: CFString
    /// Whether the items live in the data protection keychain (`kSecUseDataProtectionKeychain`). On macOS that needs a
    /// keychain entitlement: without it, storing and deleting throw `errSecMissingEntitlement` and loading returns `nil`.
    /// `false` selects the file-based login keychain. iOS has only the data protection keychain.
    public let useDataProtectionKeychain: Bool

    public init(
        service: String,
        accessibility: CFString = kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
        useDataProtectionKeychain: Bool = true
    ) {
        self.service = service
        self.accessibility = accessibility
        self.useDataProtectionKeychain = useDataProtectionKeychain
    }

    /// Adds `secretKey` under `account`. An existing item is never replaced: that throws `errSecDuplicateItem`.
    ///
    /// - Throws: `SqiSignKitError.secretKeyClosed` if the key has been closed, or the Keychain error.
    public func store(_ secretKey: SqiSignSecretKey, account: String) throws {
        try secretKey.withRawRepresentation { raw in
            guard let base = raw.baseAddress,
                  let value = CFDataCreateWithBytesNoCopy(
                      nil, base.assumingMemoryBound(to: UInt8.self), raw.count, kCFAllocatorNull)
            else { throw NSError(domain: NSOSStatusErrorDomain, code: Int(errSecAllocate)) }
            var query = itemQuery(account: account)
            query[kSecValueData] = value
            query[kSecAttrAccessible] = accessibility
            try check(SecItemAdd(query as CFDictionary, nil))
        }
    }

    /// The key stored under `account`, or `nil` if there is none.
    ///
    /// - Throws: the Keychain error, or the Kotlin error if the item is not a `parameterSet` secret key.
    public func secretKey(_ parameterSet: ParameterSet, account: String) throws -> SqiSignSecretKey? {
        var query = itemQuery(account: account)
        query[kSecReturnData] = true
        query[kSecMatchLimit] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        try check(status)
        guard let data = result as? Data else { throw NSError(domain: NSOSStatusErrorDomain, code: Int(errSecDecode)) }
        return try SqiSignSecretKey(parameterSet: parameterSet, bytes: data)
    }

    /// Deletes the key stored under `account`, if any.
    public func delete(account: String) throws {
        let status = SecItemDelete(itemQuery(account: account) as CFDictionary)
        if status != errSecItemNotFound { try check(status) }
    }

    private func itemQuery(account: String) -> [CFString: Any] {
        var query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: account,
        ]
        if useDataProtectionKeychain { query[kSecUseDataProtectionKeychain] = true }
        return query
    }

    private func check(_ status: OSStatus) throws {
        guard status == errSecSuccess else { throw NSError(domain: NSOSStatusErrorDomain, code: Int(status)) }
    }
}
