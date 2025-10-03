# Relatório Técnico: Correção de Erros - Ice Age 3 e Sandstorm
## Análise Profunda e Soluções para Problemas no Emulador touchHLE

---

**Data:** Outubro 2025  
**Versão:** 1.0  
**Apps Analisados:** Ice Age 3 (v1.5) e Sandstorm (v1.4.7)  
**Build touchHLE:** 7b58bb0 (branch: capy/cap-1-5d0919d7)  
**Plataforma:** Android (Qualcomm Adreno 619)

---

# SUMÁRIO EXECUTIVO

## Jogos Analisados

### Ice Age 3
- **Bundle ID:** com.chillingo.iceage3
- **Versão:** 1.5
- **OS Mínimo:** 2.2.1
- **Status:** ❌ **CRASH** - Objeto não responde a seletor

### Sandstorm
- **Bundle ID:** com.gameloft.Sandstorm
- **Versão:** 1.4.7
- **OS Mínimo:** 3.1.3 ⚠️ (não oficialmente suportado)
- **Status:** ❌ **CRASH** - Panic explícito durante parsing de NIB

---

## Erros Críticos Identificados

| # | Erro | Jogo Afetado | Prioridade | Complexidade |
|---|------|--------------|------------|--------------|
| 1 | `stringByReplacingOccurrencesOfString:withString:options:range:` não implementado | Ice Age | 🔴 ALTA | 🟡 MÉDIA |
| 2 | NIBArchive panic durante deserialização | Sandstorm | 🔴 ALTA | 🔴 ALTA |
| 3 | MPMediaItem* constantes não exportadas | Ice Age | 🟡 MÉDIA | 🟢 BAIXA |
| 4 | `__cxa_atexit` / `__cxa_finalize` não implementados | Ambos | 🟢 BAIXA | 🟡 MÉDIA |
| 5 | `stat()` parcialmente implementado | Ice Age | 🟢 BAIXA | 🟡 MÉDIA |
| 6 | `__objc_personality_v0` não implementado | Ambos | 🟢 BAIXA | 🔴 ALTA |
| 7 | `kCFCoreFoundationVersionNumber` não exportado | Sandstorm | 🟡 MÉDIA | 🟢 BAIXA |
| 8 | `dyld_stub_binder` não implementado | Sandstorm | 🟡 MÉDIA | 🔴 ALTA |

---

# PARTE 1: ANÁLISE DETALHADA DOS ERROS

## 1.1 Ice Age 3 - Erro Principal

### Stack Trace Completo
```
Panic at src/objc/messages.rs:60:13: 
Object 0x3001cbe0 (class "_touchHLE_NSString", 0x3000cf20) 
does not respond to selector "stringByReplacingOccurrencesOfString:withString:options:range:"!

Register state:
     R0: 0x3001cbe0  R1: 0x0019c978  R2: 0x001b2e04  R3: 0x001b2e14
     R4: 0x3001cbb0  R5: 0x3f48ff04  R6: 0xfffffdac  R7: 0xfffffc40
     R8: 0xfffffd90  R9: 0x00000015 R10: 0x00000000 R11: 0x00000000
    R12: 0x00000005  SP: 0xfffffb30  LR: 0x000f776c  PC: 0x00195c18

Stack trace:
 0. 0x195c18 (PC)
 1. 0xf776c (LR)
 2. 0x102850
 3. 0x102d50
 4. 0xb92c
 5. [host function]
 6. 0xaaf8
```

### Análise do Erro

**Método Faltando:**
```objc
- (NSString *)stringByReplacingOccurrencesOfString:(NSString *)target
                                        withString:(NSString *)replacement
                                           options:(NSStringCompareOptions)options
                                             range:(NSRange)range;
```

**Método Existente no touchHLE:**
```objc
- (NSString *)stringByReplacingOccurrencesOfString:(NSString *)target
                                        withString:(NSString *)replacement;
```

**Diferença:**
- Método existente: 2 parâmetros (target, replacement)
- Método requerido: 4 parâmetros (target, replacement, **options**, **range**)

**Por que o Ice Age precisa disso:**
- Substituições case-insensitive (NSCaseInsensitiveSearch)
- Substituições em range específico da string
- Controle sobre backward search, literal search, etc.

### Código Atual (src/frameworks/foundation/ns_string.rs:834-869)

```rust
- (id)stringByReplacingOccurrencesOfString:(id)target // NSString*
                                withString:(id)replacement { // NSString*
    // TODO: support foreign subclasses (perhaps via a helper function that
    // copies the string first)
    let mut main_iter = env.objc.borrow::<StringHostObject>(this)
        .iter_code_units();
    let target_iter = env.objc.borrow::<StringHostObject>(target)
        .iter_code_units();
    let replacement_iter = env.objc.borrow::<StringHostObject>(replacement)
        .iter_code_units();

    // TODO: zero-length target support?
    assert!(target_iter.clone().next().is_some());

    let mut result: Utf16String = Vec::new();
    loop {
        if let Some(new_main_iter) = main_iter.strip_prefix(&target_iter) {
            // matched target, replace it
            result.extend(replacement_iter.clone());
            main_iter = new_main_iter;
        } else {
            // no match, copy as normal
            match main_iter.next() {
                Some(cur) => result.push(cur),
                None => break,
            }
        }
    }

    // TODO: For a foreign subclass of NSString, do we have to return that
    // subclass? The signature implies this isn't the case and it's probably not
    // worth the effort, but it's an interesting question.
    let result_ns_string = msg_class![env; _touchHLE_NSString alloc];
    *env.objc.borrow_mut(result_ns_string) = StringHostObject::Utf16(result);
    autorelease(env, result_ns_string)
}
```

**Limitações:**
- ❌ Sem suporte a `options` (case-insensitive, backwards, etc)
- ❌ Sem suporte a `range` (substitui na string inteira)

---

## 1.2 Sandstorm - Erro Principal

### Stack Trace Completo
```
touchHLE::frameworks::foundation::ns_keyed_unarchiver: Detected NIBArchive format, converting to plist...
Panic at src/objc/objects.rs:279:17: explicit panic

Register state:
     R0: 0x374fa910  R1: 0x004671aa  R2: 0x374fa910  R3: 0x00000011
     R4: 0x00000000  R5: 0x00000000  R6: 0x00000000  R7: 0xfffffee0
     R8: 0x00000000  R9: 0x3f490e78 R10: 0x00000000 R11: 0x00000000
    R12: 0x004a3714  SP: 0xfffffea0  LR: 0x00002f54  PC: 0x0045dcf4

Stack trace:
 0. 0x45dcf4 (PC)
 1. 0x2f54 (LR)
 2. 0x2ee8
```

### Análise do Erro

**Contexto:**
1. NIBArchive foi detectado e conversão para plist iniciou com sucesso
2. Durante deserialização, ocorreu um panic em `borrow_mut`
3. O panic acontece quando um downcast falha na hierarquia de classes

**Código do Panic (src/objc/objects.rs:264-282):**

```rust
pub fn borrow_mut<T: AnyHostObject + 'static>(&mut self, object: id) -> &mut T {
    type Aho = dyn AnyHostObject + 'static;
    let mut host_object: &mut Aho = &mut *self.objects.get_mut(&object).unwrap().host_object;
    loop {
        if let Some(res) = unsafe { &mut *(host_object as *mut Aho) }
            .as_any_mut()
            .downcast_mut()
        {
            return res;  // Sucesso
        } else if let Some(next) = host_object.as_superclass_mut() {
            host_object = next;  // Tenta superclass
        } else {
            panic!();  // LINHA 279 - Falha total
        }
    }
}
```

**Possíveis Causas:**

1. **Classe Faltando:** NIB contém referência a classe não implementada
   - Candidatos: UITableView, UITableViewCell, UINavigationBar, UIToolbar
   
2. **Host Object Incompatível:** Classe existe mas com tipo de host object errado
   - Código tenta fazer borrow com tipo T incorreto
   
3. **Hierarquia de Classes Quebrada:** Superclass chain não configurada corretamente
   - as_superclass_mut() retorna None quando não deveria

**Código de Deserialização (ns_keyed_unarchiver.rs:666-685):**

```rust
let class_name = class_dict["$classname"].as_string().unwrap();

class = {
    let class_name = class_name.to_string();
    env.objc.get_known_class(&class_name, &mut env.mem)  // Pode falhar aqui
};

// ...

let new_object: id = msg![env; class alloc];
let new_object: id = msg![env; new_object initWithCoder:unarchiver];  // Ou aqui
```

**Classes UIKit NÃO Implementadas:**
- ❌ UITableView
- ❌ UITableViewCell
- ❌ UITableViewController
- ❌ UINavigationBar
- ❌ UIToolbar
- ❌ UITabBar / UITabBarController
- ❌ UISearchBar
- ❌ UIActionSheet (pode existir em NIBs antigos)

---

## 1.3 Erros Secundários

### 1.3.1 MPMediaItem - Símbolos Não Exportados

**Erros em Ice Age:**
```
touchHLE::dyld: Warning: unhandled non-lazy symbol "_MPMediaItemPropertyAlbumArtist" at 0x1af498
touchHLE::dyld: Warning: unhandled non-lazy symbol "_MPMediaItemPropertyAlbumTrackNumber" at 0x1af4a0
touchHLE::dyld: Warning: unhandled non-lazy symbol "_MPMediaItemPropertyArtist" at 0x1af4a4
touchHLE::dyld: Warning: unhandled non-lazy symbol "_MPMediaItemPropertyPlaybackDuration" at 0x1af4ac
touchHLE::dyld: Warning: unhandled non-lazy symbol "_MPMediaItemPropertyDiscCount" at 0x1af4b0
touchHLE::dyld: Warning: unhandled non-lazy symbol "_MPMediaItemPropertyArtwork" at 0x1af4b4
touchHLE::dyld: Warning: unhandled non-lazy symbol "_MPMediaItemPropertyGenre" at 0x1af4b8
touchHLE::dyld: Warning: unhandled non-lazy symbol "_MPMediaItemPropertyTitle" at 0x1af4bc
touchHLE::dyld: Warning: unhandled non-lazy symbol "_MPMediaItemPropertyAlbumTrackCount" at 0x1af4cc
touchHLE::dyld: Warning: unhandled non-lazy symbol "_MPMediaItemPropertyDiscNumber" at 0x1af4d0
touchHLE::dyld: Warning: unhandled non-lazy symbol "_MPMediaItemPropertyAlbumTitle" at 0x1af4d8
```

**Análise:**
- Ice Age usa MPMediaItem (biblioteca de música do dispositivo)
- Provavelmente para tocar música do usuário no jogo
- `MPMediaItem` classe NÃO está implementada
- Apenas `MPMoviePlayerController` (vídeo) está implementado
- Constantes de propriedades NÃO estão exportadas

### 1.3.2 MPMoviePlayer - Símbolos Não Exportados (Sandstorm)

**Erros em Sandstorm:**
```
touchHLE::dyld: Warning: unhandled non-lazy symbol "_MPMoviePlayerLoadStateDidChangeNotification" at 0x4a33b8
touchHLE::dyld: Warning: unhandled non-lazy symbol "_MPMoviePlayerPlaybackStateDidChangeNotification" at 0x4a33b0
```

**Análise:**
- Estas notificações **ESTÃO** implementadas em `movie_player.rs`
- **MAS** não estão sendo exportadas corretamente pelo dyld
- O código define as constantes (linhas 57-67)
- O CONSTANTS array existe (linhas 70-91)
- **Problema:** Pode não estar registrado em `frameworks.rs`

### 1.3.3 `__cxa_atexit` e `__cxa_finalize`

**Problema:**
- Implementação atual apenas imprime TODO e retorna sucesso
- Não registra destructors para serem chamados na saída
- Pode causar memory leaks ao sair do app
- Geralmente não causa crash, mas em apps C++ complexos pode ser problemático

**Código Atual (src/libc/cxxabi.rs:16-35):**
```rust
fn __cxa_atexit(
    _env: &mut Environment,
    func: GuestFunction,
    p: MutVoidPtr,
    d: MutVoidPtr,
) -> i32 {
    log!(
        "TODO: __cxa_atexit({:?}, {:?}, {:?}) (unimplemented)",
        func, p, d
    );
    0 // success
}

fn __cxa_finalize(_env: &mut Environment, d: MutVoidPtr) {
    log!("TODO: __cxa_finalize({:?}) (unimplemented)", d);
}
```

**Impacto:**
- ⚠️ **Geralmente não crítico durante execução**
- ❌ **Pode causar problemas ao sair do app**
- ❌ **Memory leaks de objetos globais C++**
- ❌ **Destructors não são chamados**

### 1.3.4 `stat()` Parcialmente Implementado

**Código Atual (src/libc/posix_io/stat.rs:91-124):**
```rust
fn fstat_inner(env: &mut Environment, fd: FileDescriptor, buf: MutPtr<stat>) -> i32 {
    // FIXME: This implementation is highly incomplete. fstat() returns a huge
    // struct with many kinds of data in it. This code is assuming the caller
    // only wants a small part of it.

    let mut stat = stat::default();

    match file.file {
        GuestFile::File(_) | GuestFile::IpaBundleFile(_) | GuestFile::ResourceFile(_) => {
            stat.st_mode |= S_IFREG;
            stat.st_size = file.file.stream_len().unwrap().try_into().unwrap();
        }
        GuestFile::Directory => {
            stat.st_mode |= S_IFDIR;
            // TODO: st_size
        }
        _ => unimplemented!(),
    }

    env.mem.write(buf, stat);
    0
}
```

**Campos Preenchidos:**
- ✅ `st_mode` (file type: regular file ou directory)
- ✅ `st_size` (apenas para files, não directories)

**Campos NÃO Preenchidos:**
- ❌ `st_dev` (device ID)
- ❌ `st_ino` (inode number)
- ❌ `st_nlink` (number of hard links)
- ❌ `st_uid` / `st_gid` (user/group IDs)
- ❌ `st_atimespec` / `st_mtimespec` / `st_ctimespec` (timestamps)
- ❌ `st_blocks` / `st_blksize` (block info)

**Impacto:**
- Ice Age chama `stat()` 11 vezes durante inicialização
- ⚠️ **Warnings mas geralmente não causa crash**
- ❌ **Pode causar bugs se o jogo depende de timestamps ou permissions**

---

## 1.4 Outros Erros

### 1.4.1 `__objc_personality_v0`

**Função:** Exception handling do Objective-C (ABI do ARM)
**Código Atual:** Retorna 0 (stub)
**Impacto:** Exception handling não funciona
**Complexidade:** ALTA (requer implementação completa do unwinding)

### 1.4.2 `kCFCoreFoundationVersionNumber`

**Tipo:** Constante double com versão do Core Foundation
**Exemplo:** 675.0 (iOS 4.0), 550.0 (iPhone OS 3.2)
**Uso:** Apps verificam versão para detectar features disponíveis
**Impacto:** MÉDIO - app pode desabilitar features "não disponíveis"

### 1.4.3 `dyld_stub_binder`

**Função:** Lazy binding de símbolos dinâmicos
**Uso:** Primeira vez que função dylib é chamada
**Impacto:** ALTO se usado - mas geralmente pré-bound no iOS

---

# PARTE 2: SOLUÇÕES TÉCNICAS

## 2.1 CORREÇÃO #1: NSString - Método com Options e Range

### Prioridade: 🔴 ALTA - Corrige Ice Age

### Implementação Completa

**Arquivo:** `src/frameworks/foundation/ns_string.rs`

**Adicionar após linha 869 (depois do método de 2 parâmetros):**

```rust
- (id)stringByReplacingOccurrencesOfString:(id)target // NSString*
                                withString:(id)replacement // NSString*
                                   options:(NSStringCompareOptions)options
                                     range:(NSRange)range {
    // TODO: support foreign subclasses
    
    // Validate parameters
    if target == nil || replacement == nil {
        // Should raise NSInvalidArgumentException
        log!("Warning: stringByReplacingOccurrences called with nil parameter");
        return retain(env, this);
    }
    
    // Get string content
    let main_string = to_rust_string(env, this);
    let target_str = to_rust_string(env, target);
    let replacement_str = to_rust_string(env, replacement);
    
    // Validate range
    let str_len = main_string.chars().count();
    if range.location as usize > str_len || 
       (range.location + range.length) as usize > str_len {
        log!("Warning: stringByReplacingOccurrences called with invalid range");
        return retain(env, this);
    }
    
    // Extract the portion to search
    let chars: Vec<char> = main_string.chars().collect();
    let start = range.location as usize;
    let end = (range.location + range.length) as usize;
    
    let before: String = chars[..start].iter().collect();
    let search_portion: String = chars[start..end].iter().collect();
    let after: String = chars[end..].iter().collect();
    
    // Perform replacement based on options
    let result_portion = if options & NSCaseInsensitiveSearch != 0 {
        // Case-insensitive search
        replace_case_insensitive(&search_portion, &target_str, &replacement_str, options)
    } else if options & NSLiteralSearch != 0 {
        // Literal search (default behavior)
        if options & NSBackwardsSearch != 0 {
            replace_backwards(&search_portion, &target_str, &replacement_str)
        } else {
            search_portion.replace(&target_str, &replacement_str)
        }
    } else {
        // Default: literal forward search
        search_portion.replace(&target_str, &replacement_str)
    };
    
    // Reconstruct string
    let final_string = format!("{}{}{}", before, result_portion, after);
    
    log_dbg!(
        "stringByReplacingOccurrences: '{}' -> '{}' (target: '{}', replacement: '{}', options: {:#x}, range: {:?})",
        main_string, final_string, target_str, replacement_str, options, range
    );
    
    from_rust_string(env, final_string)
}
```

### Funções Auxiliares (adicionar no topo do arquivo, após imports)

```rust
/// Replace all occurrences case-insensitively
fn replace_case_insensitive(
    text: &str,
    target: &str,
    replacement: &str,
    options: NSStringCompareOptions,
) -> String {
    if target.is_empty() {
        return text.to_string();
    }
    
    let text_lower = text.to_lowercase();
    let target_lower = target.to_lowercase();
    
    let mut result = String::new();
    let mut remaining = text;
    let mut remaining_lower = text_lower.as_str();
    
    if options & NSBackwardsSearch != 0 {
        // Backwards: start from end
        while let Some(pos) = remaining_lower.rfind(&target_lower) {
            let (before, rest) = remaining.split_at(pos);
            let (matched, after) = rest.split_at(target.len());
            
            result.insert_str(0, after);
            result.insert_str(0, replacement);
            
            remaining = before;
            remaining_lower = &text_lower[..pos];
        }
        result.insert_str(0, remaining);
    } else {
        // Forwards: start from beginning
        while let Some(pos) = remaining_lower.find(&target_lower) {
            let (before, rest) = remaining.split_at(pos);
            let (_, after) = rest.split_at(target.len());
            
            result.push_str(before);
            result.push_str(replacement);
            
            remaining = after;
            remaining_lower = &remaining.to_lowercase();
        }
        result.push_str(remaining);
    }
    
    result
}

/// Replace from end to beginning
fn replace_backwards(text: &str, target: &str, replacement: &str) -> String {
    if target.is_empty() {
        return text.to_string();
    }
    
    let mut result = String::new();
    let mut remaining = text;
    
    while let Some(pos) = remaining.rfind(target) {
        let (before, rest) = remaining.split_at(pos);
        let (_, after) = rest.split_at(target.len());
        
        result.insert_str(0, after);
        result.insert_str(0, replacement);
        
        remaining = before;
    }
    result.insert_str(0, remaining);
    result
}
```

### Alternativa Simples (se a implementação completa é muito complexa)

**Implementação Mínima (apenas suporta options=0 e range):**

```rust
- (id)stringByReplacingOccurrencesOfString:(id)target // NSString*
                                withString:(id)replacement // NSString*
                                   options:(NSStringCompareOptions)options
                                     range:(NSRange)range {
    if options != 0 {
        log!(
            "Warning: stringByReplacingOccurrencesOfString:withString:options:range: \
             options {:#x} not fully supported, using default behavior",
            options
        );
    }
    
    // For now, ignore options and range - just do full string replacement
    // This is NOT correct but prevents crash
    msg![env; this stringByReplacingOccurrencesOfString:target withString:replacement]
}
```

**Vantagens:**
- ✅ Previne crash do Ice Age
- ✅ Simples de implementar

**Desvantagens:**
- ❌ Não respeita options (case-insensitive, etc)
- ❌ Não respeita range
- ❌ Pode causar bugs visuais ou de gameplay

---

## 2.2 CORREÇÃO #2: MPMediaItem - Classes e Constantes

### Prioridade: 🟡 MÉDIA - Previne warnings no Ice Age

### Implementação

**Arquivo Novo:** `src/frameworks/media_player/media_item.rs`

```rust
/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
//! `MPMediaItem` and related classes.

use crate::dyld::{ConstantExports, HostConstant};
use crate::objc::{id, nil, objc_classes, ClassExports, HostObject};

// MPMediaItem property keys (NSString constants)
pub const MPMediaItemPropertyPersistentID: &str = "persistentID";
pub const MPMediaItemPropertyMediaType: &str = "mediaType";
pub const MPMediaItemPropertyTitle: &str = "title";
pub const MPMediaItemPropertyAlbumTitle: &str = "albumTitle";
pub const MPMediaItemPropertyAlbumPersistentID: &str = "albumPersistentID";
pub const MPMediaItemPropertyArtist: &str = "artist";
pub const MPMediaItemPropertyAlbumArtist: &str = "albumArtist";
pub const MPMediaItemPropertyGenre: &str = "genre";
pub const MPMediaItemPropertyComposer: &str = "composer";
pub const MPMediaItemPropertyPlaybackDuration: &str = "playbackDuration";
pub const MPMediaItemPropertyAlbumTrackNumber: &str = "albumTrackNumber";
pub const MPMediaItemPropertyAlbumTrackCount: &str = "albumTrackCount";
pub const MPMediaItemPropertyDiscNumber: &str = "discNumber";
pub const MPMediaItemPropertyDiscCount: &str = "discCount";
pub const MPMediaItemPropertyArtwork: &str = "artwork";
pub const MPMediaItemPropertyLyrics: &str = "lyrics";
pub const MPMediaItemPropertyIsCompilation: &str = "isCompilation";
pub const MPMediaItemPropertyReleaseDate: &str = "releaseDate";
pub const MPMediaItemPropertyBeatsPerMinute: &str = "beatsPerMinute";
pub const MPMediaItemPropertyComments: &str = "comments";
pub const MPMediaItemPropertyAssetURL: &str = "assetURL";
pub const MPMediaItemPropertyIsCloudItem: &str = "isCloudItem";
pub const MPMediaItemPropertyPodcastTitle: &str = "podcastTitle";
pub const MPMediaItemPropertyPlayCount: &str = "playCount";
pub const MPMediaItemPropertySkipCount: &str = "skipCount";
pub const MPMediaItemPropertyRating: &str = "rating";
pub const MPMediaItemPropertyLastPlayedDate: &str = "lastPlayedDate";
pub const MPMediaItemPropertyUserGrouping: &str = "userGrouping";

/// Export these constants for dyld
pub const CONSTANTS: ConstantExports = &[
    (
        "_MPMediaItemPropertyPersistentID",
        HostConstant::NSString(MPMediaItemPropertyPersistentID),
    ),
    (
        "_MPMediaItemPropertyMediaType",
        HostConstant::NSString(MPMediaItemPropertyMediaType),
    ),
    (
        "_MPMediaItemPropertyTitle",
        HostConstant::NSString(MPMediaItemPropertyTitle),
    ),
    (
        "_MPMediaItemPropertyAlbumTitle",
        HostConstant::NSString(MPMediaItemPropertyAlbumTitle),
    ),
    (
        "_MPMediaItemPropertyAlbumPersistentID",
        HostConstant::NSString(MPMediaItemPropertyAlbumPersistentID),
    ),
    (
        "_MPMediaItemPropertyArtist",
        HostConstant::NSString(MPMediaItemPropertyArtist),
    ),
    (
        "_MPMediaItemPropertyAlbumArtist",
        HostConstant::NSString(MPMediaItemPropertyAlbumArtist),
    ),
    (
        "_MPMediaItemPropertyGenre",
        HostConstant::NSString(MPMediaItemPropertyGenre),
    ),
    (
        "_MPMediaItemPropertyComposer",
        HostConstant::NSString(MPMediaItemPropertyComposer),
    ),
    (
        "_MPMediaItemPropertyPlaybackDuration",
        HostConstant::NSString(MPMediaItemPropertyPlaybackDuration),
    ),
    (
        "_MPMediaItemPropertyAlbumTrackNumber",
        HostConstant::NSString(MPMediaItemPropertyAlbumTrackNumber),
    ),
    (
        "_MPMediaItemPropertyAlbumTrackCount",
        HostConstant::NSString(MPMediaItemPropertyAlbumTrackCount),
    ),
    (
        "_MPMediaItemPropertyDiscNumber",
        HostConstant::NSString(MPMediaItemPropertyDiscNumber),
    ),
    (
        "_MPMediaItemPropertyDiscCount",
        HostConstant::NSString(MPMediaItemPropertyDiscCount),
    ),
    (
        "_MPMediaItemPropertyArtwork",
        HostConstant::NSString(MPMediaItemPropertyArtwork),
    ),
    (
        "_MPMediaItemPropertyLyrics",
        HostConstant::NSString(MPMediaItemPropertyLyrics),
    ),
    (
        "_MPMediaItemPropertyIsCompilation",
        HostConstant::NSString(MPMediaItemPropertyIsCompilation),
    ),
    (
        "_MPMediaItemPropertyReleaseDate",
        HostConstant::NSString(MPMediaItemPropertyReleaseDate),
    ),
    (
        "_MPMediaItemPropertyBeatsPerMinute",
        HostConstant::NSString(MPMediaItemPropertyBeatsPerMinute),
    ),
    (
        "_MPMediaItemPropertyComments",
        HostConstant::NSString(MPMediaItemPropertyComments),
    ),
    (
        "_MPMediaItemPropertyAssetURL",
        HostConstant::NSString(MPMediaItemPropertyAssetURL),
    ),
];

/// MPMediaItem host object (stub - just stores properties)
struct MPMediaItemHostObject {
    properties: std::collections::HashMap<String, id>,
}
impl HostObject for MPMediaItemHostObject {}

pub const CLASSES: ClassExports = objc_classes! {

(env, this, _cmd);

@implementation MPMediaItem: NSObject

+ (id)alloc {
    let host_object = Box::new(MPMediaItemHostObject {
        properties: std::collections::HashMap::new(),
    });
    env.objc.alloc_object(this, host_object, &mut env.mem)
}

- (id)valueForProperty:(id)property { // NSString*
    if property == nil {
        return nil;
    }
    
    let property_str = to_rust_string(env, property);
    let host_obj = env.objc.borrow::<MPMediaItemHostObject>(this);
    
    if let Some(&value) = host_obj.properties.get(&property_str) {
        value
    } else {
        log_dbg!("MPMediaItem: property '{}' not found, returning nil", property_str);
        nil
    }
}

// For internal use: set properties
- (())_setValue:(id)value forProperty:(id)property {
    if property == nil {
        return;
    }
    
    let property_str = to_rust_string(env, property);
    let host_obj = env.objc.borrow_mut::<MPMediaItemHostObject>(this);
    
    if value != nil {
        retain(env, value);
    }
    
    if let Some(old_value) = host_obj.properties.insert(property_str, value) {
        if old_value != nil {
            release(env, old_value);
        }
    }
}

- (())dealloc {
    let host_obj = env.objc.borrow_mut::<MPMediaItemHostObject>(this);
    for (_, value) in host_obj.properties.drain() {
        if value != nil {
            release(env, value);
        }
    }
}

@end

};
```

### Integração

**Arquivo:** `src/frameworks/media_player.rs`

```rust
pub mod media_item;  // ADICIONAR esta linha

#[derive(Default)]
pub struct State {
    movie_player: movie_player::State,
}
```

**Arquivo:** `src/frameworks/frameworks.rs` (adicionar nas listas de exports)

```rust
// Em CLASSES:
media_player::media_item::CLASSES,

// Em CONSTANTS:
media_player::media_item::CONSTANTS,
```

---

## 2.3 CORREÇÃO #3: UITableView - Implementação Básica

### Prioridade: 🔴 ALTA - Corrige Sandstorm (provavelmente)

### Diagnóstico do Problema Sandstorm

**Hipótese Mais Provável:**
- NIB contém UITableView ou UITableViewCell
- `get_known_class("UITableView")` falha ou retorna placeholder
- Durante `initWithCoder:`, código tenta fazer borrow com tipo específico
- Downcast falha → panic

**Evidência:**
- Sandstorm é jogo complexo (Gameloft)
- NIBs de jogos complexos frequentemente usam UITableView para menus
- Crash acontece exatamente após "converting to plist"
- Panic em borrow_mut sugere tipo incompatível

### Implementação Mínima de UITableView

**Arquivo Novo:** `src/frameworks/uikit/ui_view/ui_table_view.rs`

```rust
/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
//! `UITableView` and `UITableViewCell`.

use crate::frameworks::foundation::{ns_array, NSInteger, NSUInteger};
use crate::frameworks::core_graphics::CGRect;
use crate::objc::{
    id, msg, msg_class, nil, objc_classes, ClassExports, 
    HostObject, NSZonePtr, release, retain,
};

// UITableViewStyle
type UITableViewStyle = NSInteger;
#[allow(dead_code)]
const UITableViewStylePlain: UITableViewStyle = 0;
#[allow(dead_code)]
const UITableViewStyleGrouped: UITableViewStyle = 1;

// UITableViewCellStyle
type UITableViewCellStyle = NSInteger;
#[allow(dead_code)]
const UITableViewCellStyleDefault: UITableViewCellStyle = 0;
#[allow(dead_code)]
const UITableViewCellStyleValue1: UITableViewCellStyle = 1;
#[allow(dead_code)]
const UITableViewCellStyleValue2: UITableViewCellStyle = 2;
#[allow(dead_code)]
const UITableViewCellStyleSubtitle: UITableViewCellStyle = 3;

// UITableViewCellSeparatorStyle
type UITableViewCellSeparatorStyle = NSInteger;
#[allow(dead_code)]
const UITableViewCellSeparatorStyleNone: UITableViewCellSeparatorStyle = 0;
#[allow(dead_code)]
const UITableViewCellSeparatorStyleSingleLine: UITableViewCellSeparatorStyle = 1;

// UITableViewCellAccessoryType
type UITableViewCellAccessoryType = NSInteger;
#[allow(dead_code)]
const UITableViewCellAccessoryNone: UITableViewCellAccessoryType = 0;
#[allow(dead_code)]
const UITableViewCellAccessoryDisclosureIndicator: UITableViewCellAccessoryType = 1;
#[allow(dead_code)]
const UITableViewCellAccessoryDetailDisclosureButton: UITableViewCellAccessoryType = 2;
#[allow(dead_code)]
const UITableViewCellAccessoryCheckmark: UITableViewCellAccessoryType = 3;

struct UITableViewHostObject {
    data_source: id,
    delegate: id,
    style: UITableViewStyle,
    separator_style: UITableViewCellSeparatorStyle,
    row_height: f32,
    section_header_height: f32,
    section_footer_height: f32,
    allows_selection: bool,
}
impl HostObject for UITableViewHostObject {}

struct UITableViewCellHostObject {
    text_label: id,
    detail_text_label: id,
    image_view: id,
    content_view: id,
    accessory_type: UITableViewCellAccessoryType,
    style: UITableViewCellStyle,
    reuse_identifier: id,
    selection_style: NSInteger,
}
impl HostObject for UITableViewCellHostObject {}

pub const CLASSES: ClassExports = objc_classes! {

(env, this, _cmd);

@implementation UITableView: UIScrollView

+ (id)allocWithZone:(NSZonePtr)_zone {
    let host_object = Box::new(UITableViewHostObject {
        data_source: nil,
        delegate: nil,
        style: UITableViewStylePlain,
        separator_style: UITableViewCellSeparatorStyleSingleLine,
        row_height: 44.0,
        section_header_height: 0.0,
        section_footer_height: 0.0,
        allows_selection: true,
    });
    env.objc.alloc_object(this, host_object, &mut env.mem)
}

- (id)initWithFrame:(CGRect)frame
              style:(UITableViewStyle)style {
    let this: id = msg![env; this initWithFrame:frame];
    if this != nil {
        let host_obj = env.objc.borrow_mut::<UITableViewHostObject>(this);
        host_obj.style = style;
    }
    this
}

- (id)initWithCoder:(id)coder {
    let this: id = msg_super![env; this initWithCoder:coder];
    if this != nil {
        log_dbg!("[(UITableView*){:?} initWithCoder:{:?}]", this, coder);
        // TODO: decode properties from coder
    }
    this
}

- (id)dataSource {
    env.objc.borrow::<UITableViewHostObject>(this).data_source
}

- (())setDataSource:(id)data_source {
    let old = env.objc.borrow::<UITableViewHostObject>(this).data_source;
    if data_source != nil {
        retain(env, data_source);
    }
    if old != nil {
        release(env, old);
    }
    env.objc.borrow_mut::<UITableViewHostObject>(this).data_source = data_source;
}

- (id)delegate {
    env.objc.borrow::<UITableViewHostObject>(this).delegate
}

- (())setDelegate:(id)delegate {
    let old = env.objc.borrow::<UITableViewHostObject>(this).delegate;
    if delegate != nil {
        retain(env, delegate);
    }
    if old != nil {
        release(env, old);
    }
    env.objc.borrow_mut::<UITableViewHostObject>(this).delegate = delegate;
}

- (f32)rowHeight {
    env.objc.borrow::<UITableViewHostObject>(this).row_height
}

- (())setRowHeight:(f32)height {
    env.objc.borrow_mut::<UITableViewHostObject>(this).row_height = height;
}

- (UITableViewCellSeparatorStyle)separatorStyle {
    env.objc.borrow::<UITableViewHostObject>(this).separator_style
}

- (())setSeparatorStyle:(UITableViewCellSeparatorStyle)style {
    env.objc.borrow_mut::<UITableViewHostObject>(this).separator_style = style;
}

- (())reloadData {
    log_dbg!("[(UITableView*){:?} reloadData] (stub)", this);
    // TODO: actually reload data from dataSource
}

- (id)dequeueReusableCellWithIdentifier:(id)identifier { // NSString*
    log_dbg!("[(UITableView*){:?} dequeueReusableCellWithIdentifier:{:?}] (stub - returning nil)", this, identifier);
    // TODO: implement cell reuse pool
    nil
}

- (())dealloc {
    let host_obj = env.objc.borrow_mut::<UITableViewHostObject>(this);
    if host_obj.data_source != nil {
        release(env, host_obj.data_source);
    }
    if host_obj.delegate != nil {
        release(env, host_obj.delegate);
    }
}

@end

@implementation UITableViewCell: UIView

+ (id)allocWithZone:(NSZonePtr)_zone {
    let host_object = Box::new(UITableViewCellHostObject {
        text_label: nil,
        detail_text_label: nil,
        image_view: nil,
        content_view: nil,
        accessory_type: UITableViewCellAccessoryNone,
        style: UITableViewCellStyleDefault,
        reuse_identifier: nil,
        selection_style: 0, // Blue (default)
    });
    env.objc.alloc_object(this, host_object, &mut env.mem)
}

- (id)initWithStyle:(UITableViewCellStyle)style
    reuseIdentifier:(id)reuse_identifier { // NSString*
    
    // TODO: proper frame size
    let frame = CGRect {
        origin: crate::frameworks::core_graphics::CGPoint { x: 0.0, y: 0.0 },
        size: crate::frameworks::core_graphics::CGSize { width: 320.0, height: 44.0 },
    };
    
    let this: id = msg![env; this initWithFrame:frame];
    if this != nil {
        let host_obj = env.objc.borrow_mut::<UITableViewCellHostObject>(this);
        host_obj.style = style;
        
        if reuse_identifier != nil {
            retain(env, reuse_identifier);
        }
        host_obj.reuse_identifier = reuse_identifier;
        
        // Create content view
        let content_view: id = msg_class![env; UIView alloc];
        let content_view: id = msg![env; content_view initWithFrame:frame];
        host_obj.content_view = content_view;
        
        () = msg![env; this addSubview:content_view];
        
        // Create text label
        let text_label: id = msg_class![env; UILabel alloc];
        let text_label: id = msg![env; text_label initWithFrame:frame];
        host_obj.text_label = text_label;
        
        () = msg![env; content_view addSubview:text_label];
        
        log_dbg!("[(UITableViewCell*){:?} initWithStyle:{} reuseIdentifier:{:?}]", this, style, reuse_identifier);
    }
    this
}

- (id)initWithCoder:(id)coder {
    let this: id = msg_super![env; this initWithCoder:coder];
    if this != nil {
        log_dbg!("[(UITableViewCell*){:?} initWithCoder:{:?}]", this, coder);
        
        // Create basic subviews
        let host_obj = env.objc.borrow_mut::<UITableViewCellHostObject>(this);
        
        // TODO: decode properties from coder
    }
    this
}

- (id)textLabel {
    env.objc.borrow::<UITableViewCellHostObject>(this).text_label
}

- (id)detailTextLabel {
    env.objc.borrow::<UITableViewCellHostObject>(this).detail_text_label
}

- (id)imageView {
    env.objc.borrow::<UITableViewCellHostObject>(this).image_view
}

- (id)contentView {
    env.objc.borrow::<UITableViewCellHostObject>(this).content_view
}

- (UITableViewCellAccessoryType)accessoryType {
    env.objc.borrow::<UITableViewCellHostObject>(this).accessory_type
}

- (())setAccessoryType:(UITableViewCellAccessoryType)accessory_type {
    env.objc.borrow_mut::<UITableViewCellHostObject>(this).accessory_type = accessory_type;
}

- (id)reuseIdentifier {
    env.objc.borrow::<UITableViewCellHostObject>(this).reuse_identifier
}

- (())dealloc {
    let host_obj = env.objc.borrow_mut::<UITableViewCellHostObject>(this);
    
    if host_obj.text_label != nil {
        release(env, host_obj.text_label);
    }
    if host_obj.detail_text_label != nil {
        release(env, host_obj.detail_text_label);
    }
    if host_obj.image_view != nil {
        release(env, host_obj.image_view);
    }
    if host_obj.content_view != nil {
        release(env, host_obj.content_view);
    }
    if host_obj.reuse_identifier != nil {
        release(env, host_obj.reuse_identifier);
    }
}

@end

};
```

### Integração

**Arquivo:** `src/frameworks/uikit/ui_view.rs`

```rust
pub mod ui_table_view;  // ADICIONAR módulo
```

**Arquivo:** `src/frameworks/uikit.rs`

```rust
// Adicionar na lista de exports de classes
ui_view::ui_table_view::CLASSES,
```

---

## 2.4 CORREÇÃO #4: __cxa_atexit e __cxa_finalize

### Prioridade: 🟡 MÉDIA - Evita warnings e previne memory leaks

### Implementação Completa

**Arquivo:** `src/libc/cxxabi.rs`

```rust
use crate::abi::GuestFunction;
use crate::dyld::{export_c_func, FunctionExports};
use crate::mem::MutVoidPtr;
use crate::Environment;
use std::collections::HashMap;

/// Storage for C++ destructors registered via __cxa_atexit
#[derive(Default)]
pub struct State {
    /// Map from DSO handle to list of (destructor, argument) pairs
    dso_destructors: HashMap<u32, Vec<(GuestFunction, MutVoidPtr)>>,
}

impl State {
    fn get(env: &mut Environment) -> &mut Self {
        &mut env.libc_state.cxxabi
    }
}

fn __cxa_atexit(
    env: &mut Environment,
    func: GuestFunction,
    p: MutVoidPtr,
    d: MutVoidPtr,
) -> i32 {
    log_dbg!("__cxa_atexit({:?}, {:?}, {:?})", func, p, d);
    
    // Store destructor for later execution
    let dso_handle = d.to_bits();
    let state = State::get(env);
    
    state.dso_destructors
        .entry(dso_handle)
        .or_insert_with(Vec::new)
        .push((func, p));
    
    0 // success
}

fn __cxa_finalize(env: &mut Environment, d: MutVoidPtr) {
    log_dbg!("__cxa_finalize({:?})", d);
    
    let state = State::get(env);
    let dso_handle = d.to_bits();
    
    // Run destructors for this DSO in reverse order (LIFO)
    if let Some(destructors) = state.dso_destructors.remove(&dso_handle) {
        for (func, arg) in destructors.into_iter().rev() {
            log_dbg!("  Calling destructor {:?}({:?})", func, arg);
            
            // TODO: Handle exceptions during destructor execution
            // For now, we just call the function
            let result = env.call_guest_function(
                func,
                &[arg.to_bits()],
                &mut (),
            );
            
            if result.is_err() {
                log!("Warning: Destructor {:?} failed", func);
            }
        }
    }
}

fn ___objc_personality_v0(_env: &mut Environment) -> i32 {
    log!("TODO: ___objc_personality_v0 called (unimplemented)");
    0
}

pub const FUNCTIONS: FunctionExports = &[
    export_c_func!(__cxa_atexit(_, _, _)),
    export_c_func!(__cxa_finalize(_)),
    export_c_func!(___objc_personality_v0()),
];
```

**Atualizar:** `src/libc.rs`

```rust
pub mod cxxabi;

#[derive(Default)]
pub struct State {
    // ... outros campos ...
    pub cxxabi: cxxabi::State,  // ADICIONAR
}
```

**Limitações:**
- ⚠️ Exception handling ainda não funciona (___objc_personality_v0)
- ⚠️ Exceptions em destructors não são tratadas
- ✅ Mas previne memory leaks básicos

---

## 2.5 CORREÇÃO #5: stat() - Implementação Completa

### Prioridade: 🟢 BAIXA - Previne warnings

### Implementação Melhorada

**Arquivo:** `src/libc/posix_io/stat.rs`

```rust
fn fstat_inner(env: &mut Environment, fd: FileDescriptor, buf: MutPtr<stat>) -> i32 {
    let Some(file) = env.libc_state.posix_io.file_for_fd(fd) else {
        set_errno(env, EBADF);
        return -1;
    };

    let mut stat_data = stat::default();
    
    // Fill basic fields
    stat_data.st_dev = 1;  // Fake device ID
    stat_data.st_ino = fd as u64;  // Use FD as inode (hack)
    stat_data.st_nlink = 1;  // Always 1 link
    stat_data.st_uid = 501;  // Fake user ID (mobile user on iOS)
    stat_data.st_gid = 501;  // Fake group ID

    match file.file {
        GuestFile::File(_) | GuestFile::IpaBundleFile(_) | GuestFile::ResourceFile(_) => {
            stat_data.st_mode |= S_IFREG;
            
            // File permissions: 0644 (rw-r--r--)
            stat_data.st_mode |= 0o644;
            
            // File size
            stat_data.st_size = file.file.stream_len().unwrap().try_into().unwrap();
            
            // Block info
            stat_data.st_blksize = 4096;
            stat_data.st_blocks = (stat_data.st_size + 511) / 512;
        }
        GuestFile::Directory => {
            stat_data.st_mode |= S_IFDIR;
            
            // Directory permissions: 0755 (rwxr-xr-x)
            stat_data.st_mode |= 0o755;
            
            stat_data.st_size = 0;  // Directories have size 0 on most systems
            stat_data.st_blksize = 4096;
            stat_data.st_blocks = 0;
        }
        _ => {
            log!("Warning: fstat on unsupported file type");
            set_errno(env, EBADF);
            return -1;
        }
    }
    
    // Timestamps - use current time as placeholder
    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap();
    let now_secs = now.as_secs() as i64;
    let now_nsecs = now.subsec_nanos() as i64;
    
    stat_data.st_atimespec = timespec {
        tv_sec: now_secs,
        tv_nsec: now_nsecs,
    };
    stat_data.st_mtimespec = timespec {
        tv_sec: now_secs,
        tv_nsec: now_nsecs,
    };
    stat_data.st_ctimespec = timespec {
        tv_sec: now_secs,
        tv_nsec: now_nsecs,
    };
    stat_data.st_birthtimespec = timespec {
        tv_sec: now_secs,
        tv_nsec: now_nsecs,
    };

    env.mem.write(buf, stat_data);
    0 // success
}

fn fstat(env: &mut Environment, fd: FileDescriptor, buf: MutPtr<stat>) -> i32 {
    set_errno(env, 0);
    
    // Remove warning message since implementation is more complete now
    let result = fstat_inner(env, fd, buf);
    log_dbg!("fstat({:?}, {:?}) -> {}", fd, buf, result);
    result
}

fn stat(env: &mut Environment, path: ConstPtr<u8>, buf: MutPtr<stat>) -> i32 {
    set_errno(env, 0);
    
    // Remove warning message
    fn do_stat(env: &mut Environment, path: ConstPtr<u8>, buf: MutPtr<stat>) -> i32 {
        if path.is_null() {
            set_errno(env, EINVAL);
            return -1;
        }

        let fd = open_direct(env, path, 0);
        if fd == -1 {
            return -1; // errno already set by open_direct
        }

        let result = fstat_inner(env, fd, buf);
        assert!(close(env, fd) == 0);
        result
    }
    
    let result = do_stat(env, path, buf);
    
    log_dbg!(
        "stat({:?} {:?}, {:?}) -> {}",
        path,
        env.mem.cstr_at_utf8(path),
        buf,
        result
    );
    result
}
```

**Adicionar constante:**
```rust
use crate::libc::errno::{set_errno, EBADF, EEXIST, ENOENT, EINVAL};  // Adicionar EINVAL
```

---

## 2.6 CORREÇÃO #6: kCFCoreFoundationVersionNumber

### Prioridade: 🟡 MÉDIA - Sandstorm precisa

### Implementação

**Arquivo:** `src/frameworks/core_foundation.rs`

**Adicionar:**

```rust
use crate::dyld::{ConstantExports, HostConstant};

/// Core Foundation version numbers for different iOS versions
/// See: https://developer.apple.com/documentation/corefoundation/kcfcorefoundationversionnumber
pub const kCFCoreFoundationVersionNumber_iOS_2_0: f64 = 478.23;
pub const kCFCoreFoundationVersionNumber_iOS_2_1: f64 = 478.26;
pub const kCFCoreFoundationVersionNumber_iOS_2_2: f64 = 478.29;
pub const kCFCoreFoundationVersionNumber_iOS_3_0: f64 = 478.47;
pub const kCFCoreFoundationVersionNumber_iOS_3_1: f64 = 478.52;
pub const kCFCoreFoundationVersionNumber_iOS_3_2: f64 = 550.32;
pub const kCFCoreFoundationVersionNumber_iOS_4_0: f64 = 550.32;
pub const kCFCoreFoundationVersionNumber_iOS_4_1: f64 = 550.38;
pub const kCFCoreFoundationVersionNumber_iOS_4_2: f64 = 550.52;
pub const kCFCoreFoundationVersionNumber_iOS_4_3: f64 = 550.52;

/// Export version number (we'll report iOS 4.0)
pub const CONSTANTS: ConstantExports = &[
    (
        "_kCFCoreFoundationVersionNumber",
        HostConstant::Double(kCFCoreFoundationVersionNumber_iOS_4_0),
    ),
];
```

**Atualizar exports em:** `src/frameworks/frameworks.rs`

```rust
// Adicionar na lista de CONSTANTS:
core_foundation::CONSTANTS,
```

---

## 2.7 CORREÇÃO #7: Melhorar Mensagens de Erro

### Prioridade: 🟢 BAIXA - Facilita debugging

### Melhorar Panic Messages

**Arquivo:** `src/objc/objects.rs:279`

**ANTES:**
```rust
} else {
    panic!();
}
```

**DEPOIS:**
```rust
} else {
    let object_class = self.objects.get(&object).unwrap().class;
    let class_name = self.class_name(object_class);
    panic!(
        "Failed to downcast object {:?} of class '{}' to type {}. \
         This usually means the class is missing required host object type or \
         has incorrect superclass hierarchy.",
        object,
        class_name,
        std::any::type_name::<T>()
    );
}
```

**Benefício:**
- ✅ Mensagens de erro informativas
- ✅ Mostra qual classe está falhando
- ✅ Mostra qual tipo estava esperado
- ✅ Facilita diagnóstico de problemas similares

---

## 2.8 CORREÇÃO #8: MPPlaylist e MPMoviePlayer - Exportar Símbolos

### Prioridade: 🟡 MÉDIA - Sandstorm precisa

### Implementação

**Arquivo:** `src/frameworks/media_player/music_player.rs`

**Adicionar:**

```rust
use crate::dyld::{ConstantExports, HostConstant};

/// MPMediaPlaylist property keys
pub const MPMediaPlaylistPropertyPersistentID: &str = "playlistPersistentID";
pub const MPMediaPlaylistPropertyName: &str = "name";
pub const MPMediaPlaylistPropertyPlaylistAttributes: &str = "playlistAttributes";
pub const MPMediaPlaylistPropertySeedItems: &str = "seedItems";

pub const CONSTANTS: ConstantExports = &[
    (
        "_MPMediaPlaylistPropertyPersistentID",
        HostConstant::NSString(MPMediaPlaylistPropertyPersistentID),
    ),
    (
        "_MPMediaPlaylistPropertyName",
        HostConstant::NSString(MPMediaPlaylistPropertyName),
    ),
    (
        "_MPMediaPlaylistPropertyPlaylistAttributes",
        HostConstant::NSString(MPMediaPlaylistPropertyPlaylistAttributes),
    ),
    (
        "_MPMediaPlaylistPropertySeedItems",
        HostConstant::NSString(MPMediaPlaylistPropertySeedItems),
    ),
];
```

**Arquivo:** `src/frameworks/media_player.rs`

**Atualizar:**

```rust
pub mod music_player;

// Adicionar na struct State se necessário:
#[derive(Default)]
pub struct State {
    movie_player: movie_player::State,
}
```

**Arquivo:** `src/frameworks/frameworks.rs`

**Adicionar:**

```rust
// Em CONSTANTS:
media_player::music_player::CONSTANTS,
media_player::movie_player::CONSTANTS,  // Verificar se já existe
```

---

## 2.9 CORREÇÃO #9: NSError Constants

### Prioridade: 🟢 BAIXA - Sandstorm warning

### Implementação

**Arquivo:** `src/frameworks/foundation/ns_error.rs`

**Verificar se existe e adicionar:**

```rust
use crate::dyld::{ConstantExports, HostConstant};

/// NSError userInfo dictionary keys
pub const NSLocalizedDescriptionKey: &str = "NSLocalizedDescription";
pub const NSLocalizedFailureReasonErrorKey: &str = "NSLocalizedFailureReason";
pub const NSLocalizedRecoverySuggestionErrorKey: &str = "NSLocalizedRecoverySuggestion";
pub const NSLocalizedRecoveryOptionsErrorKey: &str = "NSLocalizedRecoveryOptions";
pub const NSRecoveryAttempterErrorKey: &str = "NSRecoveryAttempter";
pub const NSHelpAnchorErrorKey: &str = "NSHelpAnchor";
pub const NSStringEncodingErrorKey: &str = "NSStringEncodingError";
pub const NSURLErrorKey: &str = "NSURL";
pub const NSFilePathErrorKey: &str = "NSFilePath";
pub const NSErrorFailingURLStringKey: &str = "NSErrorFailingURLStringKey";

pub const CONSTANTS: ConstantExports = &[
    (
        "_NSLocalizedDescriptionKey",
        HostConstant::NSString(NSLocalizedDescriptionKey),
    ),
    (
        "_NSLocalizedFailureReasonErrorKey",
        HostConstant::NSString(NSLocalizedFailureReasonErrorKey),
    ),
    (
        "_NSURLErrorKey",
        HostConstant::NSString(NSURLErrorKey),
    ),
    (
        "_NSFilePathErrorKey",
        HostConstant::NSString(NSFilePathErrorKey),
    ),
    (
        "_NSErrorFailingURLStringKey",
        HostConstant::NSString(NSErrorFailingURLStringKey),
    ),
];
```

**Integrar em:** `src/frameworks/frameworks.rs`

---

## 2.10 CORREÇÃO #10: kCFStreamProperty Constants

### Prioridade: 🟢 BAIXA - Sandstorm warning

**Arquivo:** `src/frameworks/core_foundation/cf_socket.rs` (ou criar novo `cf_stream.rs`)

```rust
use crate::dyld::{ConstantExports, HostConstant};

pub const kCFStreamPropertyShouldCloseNativeSocket: &str = "kCFStreamPropertyShouldCloseNativeSocket";
pub const kCFStreamPropertySocketNativeHandle: &str = "kCFStreamPropertySocketNativeHandle";

pub const CONSTANTS: ConstantExports = &[
    (
        "_kCFStreamPropertyShouldCloseNativeSocket",
        HostConstant::NSString(kCFStreamPropertyShouldCloseNativeSocket),
    ),
    (
        "_kCFStreamPropertySocketNativeHandle",
        HostConstant::NSString(kCFStreamPropertySocketNativeHandle),
    ),
];
```

---

# PARTE 3: INVESTIGAÇÃO ADICIONAL - SANDSTORM NIB

## 3.1 Debugging do NIBArchive

### Como Identificar Qual Classe Está Faltando

**Opção 1: Adicionar Log em get_known_class**

**Arquivo:** `src/objc/classes.rs`

**Encontrar função `get_known_class` e adicionar log:**

```rust
pub fn get_known_class(&mut self, name: &str, mem: &mut Mem) -> Class {
    log!("DEBUG: get_known_class called for '{}'", name);  // ADICIONAR
    
    if let Some(&class) = self.class_name_map.get(name) {
        log_dbg!("  -> Found existing class");
        return class;
    }
    
    // ...
}
```

**Rodar Sandstorm novamente e verificar última classe antes do panic**

**Opção 2: Modificar Panic para Mostrar Mais Informações**

**Arquivo:** `src/objc/objects.rs:279`

```rust
} else {
    // Collect debug info before panicking
    let object_class = self.objects.get(&object).unwrap().class;
    let class_name = self.class_name(object_class);
    let host_obj_type = std::any::type_name_of_val(host_object);
    let expected_type = std::any::type_name::<T>();
    
    panic!(
        "Downcast failure in borrow_mut:\n\
         - Object: {:?}\n\
         - Class: '{}'\n\
         - Host object type: {}\n\
         - Expected type: {}\n\
         \n\
         This likely means:\n\
         1. The class '{}' is missing required host object implementation\n\
         2. Or the class hierarchy is incorrectly configured\n\
         3. Or wrong type is being requested for this object\n\
         \n\
         To fix: Implement the missing class or verify initWithCoder: implementation",
        object,
        class_name,
        host_obj_type,
        expected_type,
        class_name
    );
}
```

**Opção 3: Catch Panic e Continue com Warning**

**NÃO RECOMENDADO** - pode causar comportamento incorreto, mas útil para debugging:

```rust
} else {
    let object_class = self.objects.get(&object).unwrap().class;
    let class_name = self.class_name(object_class);
    
    log!(
        "ERROR: Failed to downcast object {:?} of class '{}' to type {}",
        object, class_name, std::any::type_name::<T>()
    );
    
    // Return a dummy/placeholder - PERIGOSO!
    // panic!(); // Keep panic for now
}
```

---

## 3.2 Classes UIKit Adicionais Necessárias

### Lista Prioritária (para Sandstorm e jogos similares)

**Alta Prioridade:**
1. ✅ UITableView (menu lists) - **IMPLEMENTAR AGORA**
2. ✅ UITableViewCell (células da tabela) - **IMPLEMENTAR AGORA**
3. 🚧 UINavigationBar (barra de navegação superior)
4. 🚧 UIToolbar (barra de ferramentas inferior)
5. 🚧 UITabBar / UITabBarController (tabs)

**Média Prioridade:**
6. 🚧 UIActionSheet (deprecated mas usado em apps antigos)
7. 🚧 UISearchBar (busca)
8. 🚧 UITableViewController (controller específico)
9. 🚧 UIActivityIndicatorView (loading spinner) - pode já existir

**Baixa Prioridade:**
10. 🚧 UIPageControl (page dots)
11. 🚧 UIProgressView (barra de progresso)
12. 🚧 UIStepper (iOS 5+)

### Stubs Mínimos para Prevenir Crashes

**Se implementação completa for muito complexa, criar stubs:**

**Arquivo:** `src/frameworks/uikit/ui_view/ui_stubs.rs`

```rust
/*
 * Stub implementations for less-common UIKit classes
 * These prevent crashes but don't provide full functionality
 */

pub const CLASSES: ClassExports = objc_classes! {

(env, this, _cmd);

@implementation UINavigationBar: UIView

+ (id)allocWithZone:(NSZonePtr)_zone {
    log!("Warning: UINavigationBar is a stub implementation");
    let host_object = Box::new(ui_view::UIViewHostObject::default());
    env.objc.alloc_object(this, host_object, &mut env.mem)
}

- (id)initWithCoder:(id)coder {
    msg_super![env; this initWithCoder:coder]
}

@end

@implementation UIToolbar: UIView

+ (id)allocWithZone:(NSZonePtr)_zone {
    log!("Warning: UIToolbar is a stub implementation");
    let host_object = Box::new(ui_view::UIViewHostObject::default());
    env.objc.alloc_object(this, host_object, &mut env.mem)
}

- (id)initWithCoder:(id)coder {
    msg_super![env; this initWithCoder:coder]
}

@end

@implementation UITabBar: UIView

+ (id)allocWithZone:(NSZonePtr)_zone {
    log!("Warning: UITabBar is a stub implementation");
    let host_object = Box::new(ui_view::UIViewHostObject::default());
    env.objc.alloc_object(this, host_object, &mut env.mem)
}

- (id)initWithCoder:(id)coder {
    msg_super![env; this initWithCoder:coder]
}

@end

@implementation UIActionSheet: UIView

+ (id)allocWithZone:(NSZonePtr)_zone {
    log!("Warning: UIActionSheet is a stub implementation");
    let host_object = Box::new(ui_view::UIViewHostObject::default());
    env.objc.alloc_object(this, host_object, &mut env.mem)
}

- (id)initWithCoder:(id)coder {
    msg_super![env; this initWithCoder:coder]
}

- (())showInView:(id)view {
    log!("Warning: UIActionSheet showInView: called (stub - doing nothing)");
}

@end

};
```

**Integrar em:** `src/frameworks/uikit.rs`

```rust
ui_view::ui_stubs::CLASSES,
```

---

# PARTE 4: PLANO DE AÇÃO

## 4.1 Correções Imediatas (Esta Semana)

### ✅ Correção 1: NSString com Options e Range
**Tempo Estimado:** 2-3 horas  
**Arquivo:** `src/frameworks/foundation/ns_string.rs`

**Passos:**
1. ✅ Implementar funções auxiliares (replace_case_insensitive, replace_backwards)
2. ✅ Adicionar método de 4 parâmetros
3. ✅ Testar com Ice Age
4. ✅ Verificar se crash foi resolvido

### ✅ Correção 2: MPMediaItem Constantes
**Tempo Estimado:** 1 hora  
**Arquivo:** `src/frameworks/media_player/media_item.rs` (novo)

**Passos:**
1. ✅ Criar arquivo media_item.rs
2. ✅ Definir constantes MPMediaItemProperty*
3. ✅ Exportar via CONSTANTS
4. ✅ Integrar em frameworks.rs
5. ✅ Testar Ice Age - warnings devem desaparecer

### ✅ Correção 3: UITableView Básico
**Tempo Estimado:** 4-6 horas  
**Arquivo:** `src/frameworks/uikit/ui_view/ui_table_view.rs` (novo)

**Passos:**
1. ✅ Criar UITableView stub com initWithCoder
2. ✅ Criar UITableViewCell stub com initWithCoder
3. ✅ Integrar em uikit exports
4. ✅ Testar Sandstorm
5. ✅ Verificar se panic foi resolvido

**Alternativa se não resolver:**
- Criar stubs para UINavigationBar, UIToolbar, UITabBar
- Adicionar logs em get_known_class para identificar classe exata

---

## 4.2 Correções de Médio Prazo (Próximas 2 Semanas)

### ✅ Correção 4: __cxa_atexit Funcional
**Tempo Estimado:** 3-4 horas  
**Arquivo:** `src/libc/cxxabi.rs`

**Passos:**
1. ✅ Criar State para armazenar destructors
2. ✅ Implementar registro em __cxa_atexit
3. ✅ Implementar execução em __cxa_finalize
4. ✅ Testar que não quebra apps existentes
5. ✅ Verificar com apps C++ intensivos

### ✅ Correção 5: stat() Completo
**Tempo Estimado:** 2 horas  
**Arquivo:** `src/libc/posix_io/stat.rs`

**Passos:**
1. ✅ Preencher todos os campos básicos
2. ✅ Usar timestamps do sistema de arquivos host (se disponível)
3. ✅ Remover warnings
4. ✅ Testar Ice Age

### ✅ Correção 6: kCFCoreFoundationVersionNumber
**Tempo Estimado:** 30 minutos  
**Arquivo:** `src/frameworks/core_foundation.rs`

**Passos:**
1. ✅ Definir constante (550.32 para iOS 4.0)
2. ✅ Exportar via dyld
3. ✅ Testar Sandstorm

### ✅ Correção 7: Melhorar Mensagens de Erro
**Tempo Estimado:** 1 hora  
**Arquivo:** `src/objc/objects.rs`

**Passos:**
1. ✅ Adicionar informação contextual nos panics
2. ✅ Melhorar mensagem em objects.rs:279
3. ✅ Melhorar mensagem em messages.rs:60
4. ✅ Facilita debugging futuro

---

## 4.3 Correções de Longo Prazo (Próximo Mês)

### 🚧 Correção 8: UITableView Funcional
**Tempo Estimado:** 2-3 dias  
**Complexidade:** ALTA

**Componentes Necessários:**
1. UITableView com dataSource protocol
2. UITableViewCell com reuse pool
3. UITableViewDelegate protocol
4. Rendering de células (via compositor CoreAnimation)
5. Scroll behavior (já existe em UIScrollView)

**Referências:**
- Ver implementação de UIScrollView
- Ver compositor em CoreAnimation
- Apple docs: [Table View Programming Guide](https://developer.apple.com/library/archive/documentation/UserExperience/Conceptual/TableView_iPhone/)

---

## 4.4 Investigação Adicional (Se Sandstorm Ainda Não Funcionar)

### Debugging Steps

**1. Adicionar Logging Extensivo**

```rust
// Em ns_keyed_unarchiver.rs:666
let class_name = class_dict["$classname"].as_string().unwrap();
log!("NIB: Creating object of class '{}'", class_name);  // ADICIONAR

class = {
    let class_name = class_name.to_string();
    let result = env.objc.get_known_class(&class_name, &mut env.mem);
    log!("NIB: Got class {:?} for '{}'", result, class_name);  // ADICIONAR
    result
};

// ...

log!("NIB: Calling alloc on class '{}'", class_name);  // ADICIONAR
let new_object: id = msg![env; class alloc];
log!("NIB: Got object {:?}", new_object);  // ADICIONAR

log!("NIB: Calling initWithCoder:");  // ADICIONAR
let new_object: id = msg![env; new_object initWithCoder:unarchiver];
log!("NIB: initWithCoder returned {:?}", new_object);  // ADICIONAR
```

**2. Extrair NIB do Sandstorm.app para Análise Manual**

```bash
# No dispositivo Android ou desktop
cd Sandstorm.app
find . -name "*.nib" -o -name "*.nib"

# Copiar NIB para desktop
# Analisar com plutil (macOS) ou ferramenta Python
```

**3. Criar Lista de Classes no NIB**

**Script Python:**
```python
import plistlib
import nibarchive

with open("MainWindow.nib", "rb") as f:
    data = f.read()
    
if data.startswith(b"NIBArchive"):
    nib = nibarchive.NIBArchive.from_bytes(data)
    classes = set()
    for obj in nib.objects():
        class_name = obj.class_name(nib.class_names()).name()
        classes.add(class_name)
    
    print("Classes used in NIB:")
    for cls in sorted(classes):
        print(f"  - {cls}")
```

---

# PARTE 5: TESTES E VALIDAÇÃO

## 5.1 Plano de Testes - Ice Age

### Teste 1: Aplicar Correção NSString

**Steps:**
1. Implementar método de 4 parâmetros
2. Compilar touchHLE
3. Rodar Ice Age
4. Verificar log - erro deve desaparecer

**Resultado Esperado:**
- ✅ Sem panic em stringByReplacingOccurrences
- ✅ App avança além do ponto de crash
- ⚠️ Pode encontrar outros erros mais adiante

### Teste 2: Aplicar Correção MPMediaItem

**Steps:**
1. Criar media_item.rs com constantes
2. Exportar em frameworks.rs
3. Recompilar
4. Rodar Ice Age

**Resultado Esperado:**
- ✅ Warnings de MPMediaItemProperty desaparecem
- ✅ App não tenta usar MPMediaItem (apenas verifica constantes)

### Teste 3: Verificar __cxa_atexit

**Steps:**
1. Implementar __cxa_atexit funcional
2. Recompilar
3. Rodar Ice Age até sair
4. Verificar se destructors são chamados

**Resultado Esperado:**
- ✅ Logs de __cxa_finalize mostram destructors sendo executados
- ✅ Sem crashes ao sair

---

## 5.2 Plano de Testes - Sandstorm

### Teste 1: Aplicar Correção UITableView

**Steps:**
1. Implementar UITableView e UITableViewCell básicos
2. Exportar classes
3. Recompilar
4. Rodar Sandstorm

**Resultado Esperado - Cenário A (UITableView era o problema):**
- ✅ Panic desaparece
- ✅ NIB carrega com sucesso
- ✅ App avança para próxima fase

**Resultado Esperado - Cenário B (outra classe está faltando):**
- ❌ Panic ainda ocorre
- 📋 Usar debugging logs para identificar classe

### Teste 2: Se UITableView Não Resolver

**Steps:**
1. Adicionar logs extensivos em get_known_class
2. Adicionar logs em convert_nibarchive_to_plist
3. Melhorar mensagem de panic
4. Rodar Sandstorm novamente
5. Verificar logs para identificar classe exata

**Candidatos:**
- UINavigationBar
- UIToolbar
- UITabBar
- UIActionSheet
- Classes internas não documentadas

---

## 5.3 Regression Testing

### Apps que NÃO podem quebrar:

**Já Funcionam:**
- Super Monkey Ball
- (outros apps listados no app compatibility database)

**Teste de Regressão:**
1. Aplicar todas as correções
2. Testar Super Monkey Ball
3. Verificar que ainda funciona
4. Testar 3-5 outros apps conhecidos

**Se algo quebrar:**
- Investigar qual correção causou regressão
- Adicionar flag/option para enable/disable correção
- Ou ajustar implementação

---

# PARTE 6: CÓDIGO COMPLETO DAS CORREÇÕES

## 6.1 Patch #1: NSString com Options e Range

**Arquivo:** `src/frameworks/foundation/ns_string.rs`

**Localização:** Adicionar após linha 869

<details>
<summary>Ver código completo (clique para expandir)</summary>

```rust
// ADICIONAR ESTAS FUNÇÕES AUXILIARES NO TOPO DO ARQUIVO (após imports, antes de impl)

/// Replace all occurrences case-insensitively
fn replace_case_insensitive(
    text: &str,
    target: &str,
    replacement: &str,
    options: NSStringCompareOptions,
) -> String {
    if target.is_empty() {
        return text.to_string();
    }
    
    let text_lower = text.to_lowercase();
    let target_lower = target.to_lowercase();
    
    let mut result = String::new();
    let mut remaining = text;
    let mut remaining_lower = text_lower.as_str();
    
    if options & NSBackwardsSearch != 0 {
        while let Some(pos) = remaining_lower.rfind(&target_lower) {
            let (before, rest) = remaining.split_at(pos);
            let (_, after) = rest.split_at(target.len());
            
            result.insert_str(0, after);
            result.insert_str(0, replacement);
            
            remaining = before;
            remaining_lower = &text_lower[..pos];
        }
        result.insert_str(0, remaining);
    } else {
        while let Some(pos) = remaining_lower.find(&target_lower) {
            let (before, rest) = remaining.split_at(pos);
            let (_, after) = rest.split_at(target.len());
            
            result.push_str(before);
            result.push_str(replacement);
            
            remaining = after;
            remaining_lower = &remaining.to_lowercase();
        }
        result.push_str(remaining);
    }
    
    result
}

/// Replace from end to beginning
fn replace_backwards(text: &str, target: &str, replacement: &str) -> String {
    if target.is_empty() {
        return text.to_string();
    }
    
    let mut result = String::new();
    let mut remaining = text;
    
    while let Some(pos) = remaining.rfind(target) {
        let (before, rest) = remaining.split_at(pos);
        let (_, after) = rest.split_at(target.len());
        
        result.insert_str(0, after);
        result.insert_str(0, replacement);
        
        remaining = before;
    }
    result.insert_str(0, remaining);
    result
}

// DEPOIS, ADICIONAR ESTE MÉTODO NA @implementation NSString (após linha 869)

- (id)stringByReplacingOccurrencesOfString:(id)target // NSString*
                                withString:(id)replacement // NSString*
                                   options:(NSStringCompareOptions)options
                                     range:(NSRange)range {
    if target == nil || replacement == nil {
        log!("Warning: stringByReplacingOccurrences called with nil parameter");
        return retain(env, this);
    }
    
    let main_string = to_rust_string(env, this);
    let target_str = to_rust_string(env, target);
    let replacement_str = to_rust_string(env, replacement);
    
    let str_len = main_string.chars().count();
    if range.location as usize > str_len || 
       (range.location + range.length) as usize > str_len {
        log!("Warning: stringByReplacingOccurrences with invalid range ({}, {}) for string length {}",
             range.location, range.length, str_len);
        return retain(env, this);
    }
    
    let chars: Vec<char> = main_string.chars().collect();
    let start = range.location as usize;
    let end = (range.location + range.length) as usize;
    
    let before: String = chars[..start].iter().collect();
    let search_portion: String = chars[start..end].iter().collect();
    let after: String = chars[end..].iter().collect();
    
    let result_portion = if options & NSCaseInsensitiveSearch != 0 {
        replace_case_insensitive(&search_portion, &target_str, &replacement_str, options)
    } else if options & NSLiteralSearch != 0 {
        if options & NSBackwardsSearch != 0 {
            replace_backwards(&search_portion, &target_str, &replacement_str)
        } else {
            search_portion.replace(&target_str, &replacement_str)
        }
    } else {
        search_portion.replace(&target_str, &replacement_str)
    };
    
    let final_string = format!("{}{}{}", before, result_portion, after);
    
    log_dbg!(
        "stringByReplacingOccurrences: range({},{}) options:{:#x} '{}' -> '{}'",
        range.location, range.length, options, 
        &main_string[start.min(main_string.len())..end.min(main_string.len())],
        &result_portion
    );
    
    from_rust_string(env, final_string)
}
```

</details>

---

## 6.2 Patch #2: MPMediaItem Completo

**Arquivo Novo:** `src/frameworks/media_player/media_item.rs`

<details>
<summary>Ver código completo (clique para expandir)</summary>

```rust
/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
//! `MPMediaItem`, `MPMediaItemCollection` and related classes.

use crate::dyld::{ConstantExports, HostConstant};
use crate::frameworks::foundation::ns_string::to_rust_string;
use crate::objc::{
    id, nil, objc_classes, release, retain, ClassExports, HostObject, NSZonePtr,
};
use std::collections::HashMap;

// MPMediaItem property keys
pub const MPMediaItemPropertyPersistentID: &str = "persistentID";
pub const MPMediaItemPropertyMediaType: &str = "mediaType";
pub const MPMediaItemPropertyTitle: &str = "title";
pub const MPMediaItemPropertyAlbumTitle: &str = "albumTitle";
pub const MPMediaItemPropertyAlbumPersistentID: &str = "albumPersistentID";
pub const MPMediaItemPropertyArtist: &str = "artist";
pub const MPMediaItemPropertyAlbumArtist: &str = "albumArtist";
pub const MPMediaItemPropertyGenre: &str = "genre";
pub const MPMediaItemPropertyComposer: &str = "composer";
pub const MPMediaItemPropertyPlaybackDuration: &str = "playbackDuration";
pub const MPMediaItemPropertyAlbumTrackNumber: &str = "albumTrackNumber";
pub const MPMediaItemPropertyAlbumTrackCount: &str = "albumTrackCount";
pub const MPMediaItemPropertyDiscNumber: &str = "discNumber";
pub const MPMediaItemPropertyDiscCount: &str = "discCount";
pub const MPMediaItemPropertyArtwork: &str = "artwork";
pub const MPMediaItemPropertyLyrics: &str = "lyrics";
pub const MPMediaItemPropertyIsCompilation: &str = "isCompilation";
pub const MPMediaItemPropertyReleaseDate: &str = "releaseDate";
pub const MPMediaItemPropertyBeatsPerMinute: &str = "beatsPerMinute";
pub const MPMediaItemPropertyComments: &str = "comments";
pub const MPMediaItemPropertyAssetURL: &str = "assetURL";
pub const MPMediaItemPropertyPlayCount: &str = "playCount";
pub const MPMediaItemPropertySkipCount: &str = "skipCount";
pub const MPMediaItemPropertyRating: &str = "rating";

pub const CONSTANTS: ConstantExports = &[
    ("_MPMediaItemPropertyPersistentID", HostConstant::NSString(MPMediaItemPropertyPersistentID)),
    ("_MPMediaItemPropertyMediaType", HostConstant::NSString(MPMediaItemPropertyMediaType)),
    ("_MPMediaItemPropertyTitle", HostConstant::NSString(MPMediaItemPropertyTitle)),
    ("_MPMediaItemPropertyAlbumTitle", HostConstant::NSString(MPMediaItemPropertyAlbumTitle)),
    ("_MPMediaItemPropertyAlbumPersistentID", HostConstant::NSString(MPMediaItemPropertyAlbumPersistentID)),
    ("_MPMediaItemPropertyArtist", HostConstant::NSString(MPMediaItemPropertyArtist)),
    ("_MPMediaItemPropertyAlbumArtist", HostConstant::NSString(MPMediaItemPropertyAlbumArtist)),
    ("_MPMediaItemPropertyGenre", HostConstant::NSString(MPMediaItemPropertyGenre)),
    ("_MPMediaItemPropertyComposer", HostConstant::NSString(MPMediaItemPropertyComposer)),
    ("_MPMediaItemPropertyPlaybackDuration", HostConstant::NSString(MPMediaItemPropertyPlaybackDuration)),
    ("_MPMediaItemPropertyAlbumTrackNumber", HostConstant::NSString(MPMediaItemPropertyAlbumTrackNumber)),
    ("_MPMediaItemPropertyAlbumTrackCount", HostConstant::NSString(MPMediaItemPropertyAlbumTrackCount)),
    ("_MPMediaItemPropertyDiscNumber", HostConstant::NSString(MPMediaItemPropertyDiscNumber)),
    ("_MPMediaItemPropertyDiscCount", HostConstant::NSString(MPMediaItemPropertyDiscCount)),
    ("_MPMediaItemPropertyArtwork", HostConstant::NSString(MPMediaItemPropertyArtwork)),
    ("_MPMediaItemPropertyLyrics", HostConstant::NSString(MPMediaItemPropertyLyrics)),
    ("_MPMediaItemPropertyIsCompilation", HostConstant::NSString(MPMediaItemPropertyIsCompilation)),
    ("_MPMediaItemPropertyReleaseDate", HostConstant::NSString(MPMediaItemPropertyReleaseDate)),
    ("_MPMediaItemPropertyBeatsPerMinute", HostConstant::NSString(MPMediaItemPropertyBeatsPerMinute)),
    ("_MPMediaItemPropertyComments", HostConstant::NSString(MPMediaItemPropertyComments)),
    ("_MPMediaItemPropertyAssetURL", HostConstant::NSString(MPMediaItemPropertyAssetURL)),
    ("_MPMediaItemPropertyPlayCount", HostConstant::NSString(MPMediaItemPropertyPlayCount)),
    ("_MPMediaItemPropertySkipCount", HostConstant::NSString(MPMediaItemPropertySkipCount)),
    ("_MPMediaItemPropertyRating", HostConstant::NSString(MPMediaItemPropertyRating)),
];

struct MPMediaItemHostObject {
    properties: HashMap<String, id>,
}
impl HostObject for MPMediaItemHostObject {}

pub const CLASSES: ClassExports = objc_classes! {

(env, this, _cmd);

@implementation MPMediaItem: NSObject

+ (id)allocWithZone:(NSZonePtr)_zone {
    let host_object = Box::new(MPMediaItemHostObject {
        properties: HashMap::new(),
    });
    env.objc.alloc_object(this, host_object, &mut env.mem)
}

- (id)valueForProperty:(id)property { // NSString*
    if property == nil {
        return nil;
    }
    
    let property_str = to_rust_string(env, property);
    let host_obj = env.objc.borrow::<MPMediaItemHostObject>(this);
    
    if let Some(&value) = host_obj.properties.get(&property_str) {
        value
    } else {
        log_dbg!("MPMediaItem: property '{}' not found", property_str);
        nil
    }
}

- (())dealloc {
    let host_obj = env.objc.borrow_mut::<MPMediaItemHostObject>(this);
    for (_, value) in host_obj.properties.drain() {
        if value != nil {
            release(env, value);
        }
    }
}

@end

@implementation MPMediaItemCollection: NSObject

+ (id)allocWithZone:(NSZonePtr)_zone {
    log!("Warning: MPMediaItemCollection is stub");
    let host_object = Box::new(());
    env.objc.alloc_object(this, host_object, &mut env.mem)
}

- (id)items {
    log!("TODO: [MPMediaItemCollection items] (stub)");
    nil
}

@end

};
```

</details>

**Integração:**
1. Criar arquivo `src/frameworks/media_player/media_item.rs`
2. Adicionar `pub mod media_item;` em `src/frameworks/media_player.rs`
3. Adicionar exports em `src/frameworks/frameworks.rs`

---

## 6.3 Patch #3: UITableView Completo

Ver **Seção 2.3** acima para código completo.

---

## 6.4 Patch #4: Mensagens de Erro Melhores

**Arquivo:** `src/objc/objects.rs`

**Linha 279 - ANTES:**
```rust
} else {
    panic!();
}
```

**Linha 279 - DEPOIS:**
```rust
} else {
    let object_class = self.objects.get(&object).unwrap().class;
    let class_name = self.class_name(object_class);
    panic!(
        "Downcast failure: Object {:?} (class '{}') cannot be borrowed as type {}. \
         This usually means the class is missing the required host object type or \
         has an incorrect superclass hierarchy.",
        object,
        class_name,
        std::any::type_name::<T>()
    );
}
```

**Arquivo:** `src/objc/messages.rs` (linha 60)

**ANTES:**
```rust
panic!("Object {:?} (class {:?}, {:?}) does not respond to selector {:?}!",
       object, class, class_name, selector_name);
```

**DEPOIS (se já não for assim):**
```rust
panic!(
    "Unimplemented selector: Object {:?} (class '{}'  [{:?}]) does not respond to selector '{}'!\n\
     \n\
     To fix: Implement this method in the appropriate class file.\n\
     Search for '@implementation {}' in src/frameworks/",
    object, class_name, class, selector_name, class_name
);
```

---

# PARTE 7: CHECKLIST DE IMPLEMENTAÇÃO

## 7.1 Implementação Passo a Passo

### ✅ FASE 1: Correções Críticas (Hoje/Amanhã)

**1.1 NSString stringByReplacing... com options e range**
- [ ] Implementar funções auxiliares (replace_case_insensitive, replace_backwards)
- [ ] Adicionar método de 4 parâmetros em ns_string.rs
- [ ] Compilar: `cargo build --release`
- [ ] Testar com Ice Age
- [ ] Verificar se crash foi resolvido

**Tempo:** ~2 horas  
**Impacto:** 🔴 ALTO - Resolve Ice Age

---

**1.2 MPMediaItem Constantes**
- [ ] Criar arquivo `src/frameworks/media_player/media_item.rs`
- [ ] Copiar código do Patch #2
- [ ] Adicionar `pub mod media_item;` em media_player.rs
- [ ] Adicionar exports em frameworks.rs (CONSTANTS e CLASSES)
- [ ] Compilar
- [ ] Testar Ice Age - warnings devem desaparecer

**Tempo:** ~1 hora  
**Impacto:** 🟡 MÉDIO - Resolve warnings

---

**1.3 UITableView Básico**
- [ ] Criar arquivo `src/frameworks/uikit/ui_view/ui_table_view.rs`
- [ ] Copiar código do Patch #1 (UITableView)
- [ ] Adicionar `pub mod ui_table_view;` em ui_view.rs
- [ ] Adicionar `ui_view::ui_table_view::CLASSES,` em uikit.rs
- [ ] Compilar
- [ ] Testar Sandstorm
- [ ] Se ainda crashar, seguir para Fase 2

**Tempo:** ~3 horas  
**Impacto:** 🔴 ALTO - Pode resolver Sandstorm

---

### 🔧 FASE 2: Se Sandstorm Ainda Crashar (Próximos Dias)

**2.1 Debugging Avançado**
- [ ] Adicionar logs em get_known_class
- [ ] Adicionar logs em convert_nibarchive_to_plist
- [ ] Melhorar mensagem de panic em objects.rs:279
- [ ] Recompilar e rodar Sandstorm
- [ ] Identificar classe exata que está faltando

**Tempo:** ~2 horas  
**Impacto:** 🟡 MÉDIO - Diagnóstico

---

**2.2 Implementar Classe Faltando (se não for UITableView)**
- [ ] Criar arquivo para classe identificada
- [ ] Implementar stub básico com initWithCoder
- [ ] Exportar classe
- [ ] Testar Sandstorm

**Tempo:** ~2-4 horas (depende da classe)  
**Impacto:** 🔴 ALTO - Resolve Sandstorm

---

### 🔧 FASE 3: Melhorias (Próximas 2 Semanas)

**3.1 __cxa_atexit Funcional**
- [ ] Implementar State em cxxabi.rs
- [ ] Implementar registro de destructors
- [ ] Implementar execução em __cxa_finalize
- [ ] Adicionar State em libc.rs
- [ ] Testar

**Tempo:** ~3 horas  
**Impacto:** 🟡 MÉDIO - Previne leaks

---

**3.2 stat() Completo**
- [ ] Preencher todos os campos de stat struct
- [ ] Usar timestamps reais
- [ ] Remover warnings
- [ ] Testar

**Tempo:** ~2 horas  
**Impacto:** 🟢 BAIXO - Melhoria

---

**3.3 kCFCoreFoundationVersionNumber**
- [ ] Adicionar constante em core_foundation.rs
- [ ] Exportar via dyld
- [ ] Testar Sandstorm

**Tempo:** ~30 minutos  
**Impacto:** 🟡 MÉDIO - Sandstorm

---

**3.4 MPPlaylist e NSError Constants**
- [ ] Adicionar MPMediaPlaylistProperty* em music_player.rs
- [ ] Adicionar NSError* keys em ns_error.rs
- [ ] Adicionar kCFStreamProperty* em cf_socket.rs
- [ ] Exportar tudo
- [ ] Testar

**Tempo:** ~1 hora  
**Impacto:** 🟢 BAIXO - Remove warnings

---

### 🏆 FASE 4: Validação Final (Após Todas as Correções)

**4.1 Testes dos Jogos**
- [ ] Ice Age: Deve rodar sem crash
- [ ] Sandstorm: Deve rodar sem crash
- [ ] Super Monkey Ball: Deve continuar funcionando (regression test)
- [ ] 3-5 outros apps: Testar compatibilidade

**Tempo:** ~2 horas  
**Impacto:** ✅ Validação

---

**4.2 Documentação**
- [ ] Atualizar CHANGELOG.md
- [ ] Documentar correções em docs/
- [ ] Criar issue no GitHub se problemas persistirem

**Tempo:** ~1 hora  
**Impacto:** 📝 Documentação

---

# PARTE 8: REFERÊNCIAS E RECURSOS

## 8.1 Apple Documentation (Archived)

### NSString
- [String Programming Guide](https://developer.apple.com/library/archive/documentation/Cocoa/Conceptual/Strings/)
- [NSString Class Reference](https://developer.apple.com/documentation/foundation/nsstring)

### UITableView
- [Table View Programming Guide](https://developer.apple.com/library/archive/documentation/UserExperience/Conceptual/TableView_iPhone/)
- [UITableView Class Reference](https://developer.apple.com/documentation/uikit/uitableview)
- [UITableViewCell Class Reference](https://developer.apple.com/documentation/uikit/uitableviewcell)

### MediaPlayer
- [Media Player Framework Reference](https://developer.apple.com/documentation/mediaplayer)
- [MPMediaItem Class Reference](https://developer.apple.com/documentation/mediaplayer/mpmediaitem)
- [MPMoviePlayerController Class Reference](https://developer.apple.com/documentation/mediaplayer/mpmovieplayercontroller)

### C++ ABI
- [Itanium C++ ABI Specification](https://itanium-cxx-abi.github.io/cxx-abi/abi.html)
- [__cxa_atexit documentation](https://itanium-cxx-abi.github.io/cxx-abi/abi.html#dso-dtor-runtime-api)

### POSIX
- [stat() POSIX Specification](https://pubs.opengroup.org/onlinepubs/009696799/functions/stat.html)

## 8.2 Bibliotecas Rust Relevantes

### nibarchive
- [docs.rs/nibarchive](https://docs.rs/nibarchive)
- [GitHub: michaelwright235/nibarchive](https://github.com/michaelwright235/nibarchive)

## 8.3 Ferramentas de Debugging

### Análise de NIB Files
- **plutil** (macOS): `plutil -p file.nib`
- **ibtool** (macOS): `ibtool --export-strings-file`
- **nibarchive** (Rust): Análise programática

### Emulator Debugging
- **GDB** (com touchHLE): Ver `dev-docs/debugging.md`
- **Logs**: `RUST_LOG=debug touchHLE app.ipa`

---

# PARTE 9: RESUMO DE PRIORIDADES

## 🔴 CRÍTICO - Implementar AGORA

### 1. NSString com Options e Range
**Por quê:** Resolve crash do Ice Age imediatamente  
**Complexidade:** MÉDIA  
**Tempo:** 2 horas  
**Arquivo:** `ns_string.rs`

### 2. UITableView Stub
**Por quê:** Provavelmente resolve crash do Sandstorm  
**Complexidade:** MÉDIA  
**Tempo:** 3 horas  
**Arquivo:** `ui_table_view.rs` (novo)

---

## 🟡 IMPORTANTE - Próxima Semana

### 3. MPMediaItem Classes e Constantes
**Por quê:** Remove warnings do Ice Age  
**Complexidade:** BAIXA  
**Tempo:** 1 hora  
**Arquivo:** `media_item.rs` (novo)

### 4. kCFCoreFoundationVersionNumber
**Por quê:** Sandstorm verifica versão  
**Complexidade:** BAIXA  
**Tempo:** 30 minutos  
**Arquivo:** `core_foundation.rs`

### 5. Mensagens de Erro Melhores
**Por quê:** Facilita debugging de problemas futuros  
**Complexidade:** BAIXA  
**Tempo:** 1 hora  
**Arquivo:** `objects.rs`, `messages.rs`

---

## 🟢 BAIXA PRIORIDADE - Próximo Mês

### 6. __cxa_atexit Funcional
**Por quê:** Previne memory leaks, melhora compatibilidade C++  
**Complexidade:** MÉDIA  
**Tempo:** 3 horas  
**Arquivo:** `cxxabi.rs`

### 7. stat() Completo
**Por quê:** Mais correto, previne bugs em apps que dependem de timestamps  
**Complexidade:** BAIXA  
**Tempo:** 2 horas  
**Arquivo:** `stat.rs`

### 8. MPPlaylist e NSError Constants
**Por quê:** Remove warnings restantes  
**Complexidade:** BAIXA  
**Tempo:** 1 hora  
**Arquivos:** `music_player.rs`, `ns_error.rs`

---

# PARTE 10: TROUBLESHOOTING GUIDE

## 10.1 Se Ice Age Continuar Crashando

### Problema: Outro método NSString faltando

**Sintoma:**
```
Object X does not respond to selector "algumMetodoNSString..."
```

**Solução:**
1. Verificar qual método está faltando no log
2. Pesquisar método na documentação da Apple
3. Implementar em ns_string.rs
4. Seguir padrão dos métodos existentes

### Problema: MPMediaLibrary é acessado

**Sintoma:**
```
TODO: [MPMediaLibrary defaultMediaLibrary] (not implemented yet)
```

**Solução:**
- Ice Age pode tentar acessar biblioteca de música
- Implementar MPMediaLibrary.defaultMediaLibrary retornando objeto vazio
- Implementar MPMediaQuery para buscar músicas (retornar array vazio)

---

## 10.2 Se Sandstorm Continuar Crashando

### Problema: Outra classe UIKit está faltando

**Sintoma:**
- Panic em borrow_mut ainda ocorre
- Mas agora com mensagem melhorada mostrando classe

**Solução:**
1. Verificar mensagem de panic melhorada
2. Identificar classe exata
3. Implementar stub básico
4. Repetir até sucesso

### Problema: NIBArchive malformado

**Sintoma:**
```
NIBArchive parsing error: ...
```

**Solução:**
- Biblioteca nibarchive pode não suportar todos os formatos
- Verificar versão do Xcode que compilou o NIB
- Considerar upgrade da biblioteca nibarchive
- Ou adicionar suporte para formato específico

### Problema: initWithCoder: falhando

**Sintoma:**
- Classes são criadas mas initWithCoder retorna nil
- Ou crash durante initWithCoder

**Solução:**
- Implementar decodificação de propriedades específicas
- Ver implementação de initWithCoder em outras classes UIKit
- Adicionar logs para ver quais keys estão sendo decodificadas

---

## 10.3 Se Ambos Ainda Não Funcionarem

### Verificar Ordem de Aplicação dos Patches

**Importante:**
1. Aplicar patches na ordem correta
2. Compilar após cada patch
3. Testar incrementalmente
4. Não aplicar todos de uma vez

### Criar Build de Debug

```bash
# Compilar com símbolos de debug e logs verbosos
RUST_LOG=trace cargo build

# Rodar com logging completo
RUST_LOG=touchHLE=trace ./target/debug/touchHLE Sandstorm.app 2>&1 | tee sandstorm_debug.log
```

### Reportar Issue no GitHub

Se após todas as correções os apps ainda não funcionarem:

1. Criar issue em https://github.com/touchHLE/touchHLE/issues
2. Incluir:
   - Build do touchHLE (commit hash)
   - Versão do app
   - Log completo
   - Patches aplicados
   - Comportamento observado vs esperado

---

# PARTE 11: CÓDIGO PARA COPY-PASTE

## 11.1 Comandos de Build

```bash
# No diretório touchHLE/

# Compilar para desktop (debug)
cargo build

# Compilar para desktop (release)
cargo build --release

# Compilar para Android (release)
cargo ndk --target aarch64-linux-android --platform 21 build --release

# Formatar código
cargo fmt

# Verificar erros
cargo clippy

# Rodar testes
cargo test
```

## 11.2 Adicionar Módulos aos Exports

### Em `src/frameworks/media_player.rs`

```rust
pub mod media_item;
pub mod media_library;
pub mod media_picker_controller;
pub mod media_query;
pub mod movie_player;
pub mod music_player;

#[derive(Default)]
pub struct State {
    movie_player: movie_player::State,
}
```

### Em `src/frameworks/uikit/ui_view.rs`

```rust
pub mod ui_alert_view;
pub mod ui_control;
pub mod ui_image_view;
pub mod ui_label;
pub mod ui_picker_view;
pub mod ui_scroll_view;
pub mod ui_table_view;  // ADICIONAR
pub mod ui_web_view;
pub mod ui_window;
```

### Em `src/frameworks/frameworks.rs`

**Adicionar em CLASSES:**
```rust
media_player::media_item::CLASSES,
uikit::ui_view::ui_table_view::CLASSES,
```

**Adicionar em CONSTANTS:**
```rust
core_foundation::CONSTANTS,
foundation::ns_error::CONSTANTS,
media_player::media_item::CONSTANTS,
media_player::music_player::CONSTANTS,
```

---

# APÊNDICES

## Apêndice A: Análise de Símbolos Não Resolvidos

### Ice Age - Símbolos Únicos Não Resolvidos

```
CRÍTICOS (causam crash):
- stringByReplacingOccurrencesOfString:withString:options:range:

MÉDIA PRIORIDADE (causam warnings):
- MPMediaItemProperty* (11 símbolos diferentes)

BAIXA PRIORIDADE (geralmente OK):
- __cxa_atexit, __cxa_finalize
- __objc_personality_v0
- ___mb_cur_max
- _OBJC_EHTYPE_$_NSException, _OBJC_EHTYPE_id
```

### Sandstorm - Símbolos Únicos Não Resolvidos

```
CRÍTICOS (podem causar crash):
- Classe UIKit faltando em NIB (UITableView provavelmente)

MÉDIA PRIORIDADE:
- kCFCoreFoundationVersionNumber
- MPMoviePlayer* notifications (já implementadas, falta exportar)
- MPMediaPlaylistPropertyName
- dyld_stub_binder

BAIXA PRIORIDADE:
- __cxa_atexit, __cxa_finalize
- __objc_personality_v0
- NSErrorFailingURLStringKey
- kCFStreamPropertyShouldCloseNativeSocket
```

---

## Apêndice B: Estrutura de stat

```c
struct stat {
    dev_t     st_dev;         // Device ID
    mode_t    st_mode;        // File mode (type + permissions)
    nlink_t   st_nlink;       // Number of hard links
    ino_t     st_ino;         // Inode number
    uid_t     st_uid;         // User ID
    gid_t     st_gid;         // Group ID
    dev_t     st_rdev;        // Device ID (if special file)
    timespec  st_atimespec;   // Last access time
    timespec  st_mtimespec;   // Last modification time
    timespec  st_ctimespec;   // Last status change time
    timespec  st_birthtimespec; // File creation time
    off_t     st_size;        // File size in bytes
    blkcnt_t  st_blocks;      // Number of 512-byte blocks allocated
    blksize_t st_blksize;     // Preferred block size for I/O
    uint32_t  st_flags;       // User defined flags
    uint32_t  st_gen;         // File generation number
    int32_t   st_lspare;      // Reserved
    int64_t   st_qspare[2];   // Reserved
};
```

**Campos Usados Frequentemente:**
- `st_mode` (tipo de arquivo: regular, directory, etc)
- `st_size` (tamanho)
- `st_mtime` / `st_mtimespec` (última modificação)

**Campos Raramente Usados:**
- `st_ino`, `st_dev` (apenas em sistemas POSIX avançados)
- `st_uid`, `st_gid` (permissions - iOS apps não verificam)
- `st_blocks`, `st_blksize` (otimização I/O)

---

## Apêndice C: NSStringCompareOptions

```objc
typedef NS_OPTIONS(NSUInteger, NSStringCompareOptions) {
    NSCaseInsensitiveSearch = 1,        // 0x01 - Ignore case
    NSLiteralSearch = 2,                // 0x02 - Exact match
    NSBackwardsSearch = 4,              // 0x04 - Search from end
    NSAnchoredSearch = 8,               // 0x08 - Must match at boundary
    NSNumericSearch = 64,               // 0x40 - Numbers compared numerically
    NSDiacriticInsensitiveSearch = 128, // 0x80 - Ignore diacritics
    NSWidthInsensitiveSearch = 256,     // 0x100 - Ignore width differences
    NSForcedOrderingSearch = 512,       // 0x200 - Force ordering
    NSRegularExpressionSearch = 1024,   // 0x400 - Regex (iOS 3.2+)
};
```

**Mais Usados em Jogos:**
- NSCaseInsensitiveSearch (ignorar maiúsculas/minúsculas)
- NSBackwardsSearch (buscar do final)
- NSLiteralSearch (busca literal)

---

## Apêndice D: Hierarquia de Classes UIKit

```
NSObject
  └─ UIResponder
      └─ UIView
          ├─ UIControl
          │   ├─ UIButton
          │   ├─ UISlider
          │   ├─ UISwitch
          │   ├─ UITextField
          │   └─ UISegmentedControl
          ├─ UILabel
          ├─ UIImageView
          ├─ UIScrollView
          │   ├─ UITableView        ← FALTANDO
          │   └─ UITextView
          ├─ UITableViewCell        ← FALTANDO
          ├─ UINavigationBar        ← FALTANDO
          ├─ UIToolbar              ← FALTANDO
          ├─ UITabBar               ← FALTANDO
          ├─ UISearchBar            ← FALTANDO
          ├─ UIWebView
          ├─ UIPickerView
          ├─ UIAlertView
          └─ UIWindow
```

---

## Apêndice E: Estimativa de Esforço Total

| Correção | Prioridade | Complexidade | Tempo | Impacto |
|----------|------------|--------------|-------|---------|
| NSString options+range | 🔴 ALTA | 🟡 MÉDIA | 2h | Ice Age |
| MPMediaItem constants | 🟡 MÉDIA | 🟢 BAIXA | 1h | Ice Age warnings |
| UITableView stub | 🔴 ALTA | 🟡 MÉDIA | 3h | Sandstorm |
| Debugging NIB | 🔴 ALTA | 🟡 MÉDIA | 2h | Sandstorm (se necessário) |
| __cxa_atexit | 🟡 MÉDIA | 🟡 MÉDIA | 3h | Geral |
| stat() completo | 🟢 BAIXA | 🟢 BAIXA | 2h | Geral |
| kCFCoreFoundation | 🟡 MÉDIA | 🟢 BAIXA | 0.5h | Sandstorm |
| Mensagens de erro | 🟢 BAIXA | 🟢 BAIXA | 1h | Debugging |
| Constants extras | 🟢 BAIXA | 🟢 BAIXA | 1h | Warnings |
| **TOTAL** | | | **15.5h** | **2 jogos** |

**Timeline Sugerida:**
- **Dia 1 (4h):** NSString + MPMediaItem
- **Dia 2 (4h):** UITableView + Testing
- **Dia 3 (3h):** Debugging Sandstorm (se necessário)
- **Dia 4 (2h):** __cxa_atexit
- **Dia 5 (2.5h):** stat() + constants + mensagens

**Total:** ~5 dias de trabalho para resolver ambos os jogos

---

# CONCLUSÃO

## Resumo das Causas

### Ice Age Crash:
- **Causa Raiz:** NSString method não implementado
- **Método Faltando:** `stringByReplacingOccurrencesOfString:withString:options:range:`
- **Correção:** Implementar versão de 4 parâmetros
- **Complexidade:** MÉDIA
- **Certeza:** 99% - erro é explícito no log

### Sandstorm Crash:
- **Causa Raiz (Provável):** UITableView não implementado
- **Evidência:** NIB parsing OK, panic em downcast, jogos complexos usam TableView
- **Correção:** Implementar UITableView stub
- **Complexidade:** MÉDIA-ALTA
- **Certeza:** 80% - requer debugging adicional se não resolver

### Outros Problemas:
- **__cxa_atexit:** Não causa crash, mas deve ser implementado
- **stat():** Funcional mas incompleto
- **MPMediaItem:** Apenas warnings, não crítico
- **Constants:** Apenas warnings, fácil de corrigir

## Próximos Passos Imediatos

**Para Resolver Ice Age (hoje):**
1. ✅ Implementar NSString stringByReplacingOccurrences com options e range
2. ✅ Implementar MPMediaItem constants
3. ✅ Compilar e testar

**Para Resolver Sandstorm (amanhã):**
1. ✅ Implementar UITableView e UITableViewCell stubs
2. ✅ Adicionar kCFCoreFoundationVersionNumber
3. ✅ Compilar e testar
4. 🔍 Se não resolver: debugging adicional

**Melhorias Gerais (próxima semana):**
1. ✅ Implementar __cxa_atexit funcional
2. ✅ Completar stat()
3. ✅ Melhorar mensagens de erro
4. ✅ Adicionar constants faltantes

---

**FIM DO RELATÓRIO**

---

**Versão:** 1.0  
**Data:** Outubro 2025  
**Status:** ✅ Análise Completa - Pronto para Implementação  
**Próxima Ação:** Implementar Patches #1, #2 e #3
