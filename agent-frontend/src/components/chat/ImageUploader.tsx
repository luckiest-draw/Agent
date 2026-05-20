import { useRef, useState } from 'react';
import { ImagePlus, X } from 'lucide-react';
import { api } from '@/api/client';

interface ImageInfo {
  id: string;
  file: File;
  localUrl: string;
  serverUrl: string | null;
  uploading: boolean;
  error: boolean;
}

interface Props {
  images: ImageInfo[];
  onChange: (images: ImageInfo[]) => void;
}

export default function ImageUploader({ images, onChange }: Props) {
  const inputRef = useRef<HTMLInputElement>(null);

  const handleSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files) return;

    const newImages: ImageInfo[] = [];
    for (const file of files) {
      newImages.push({
        id: crypto.randomUUID(),
        file,
        localUrl: URL.createObjectURL(file),
        serverUrl: null,
        uploading: true,
        error: false,
      });
    }

    const updated = [...images, ...newImages];
    onChange(updated);

    for (const img of newImages) {
      api.uploadImage(img.file)
        .then(res => {
          if (res.code === 200) {
            onChange((prev: ImageInfo[]) =>
              prev.map(p =>
                p.id === img.id ? { ...p, serverUrl: res.data.imageUrl, uploading: false } : p
              )
            );
          } else {
            onChange((prev: ImageInfo[]) =>
              prev.map(p => (p.id === img.id ? { ...p, uploading: false, error: true } : p))
            );
          }
        })
        .catch(() => {
          onChange((prev: ImageInfo[]) =>
            prev.map(p => (p.id === img.id ? { ...p, uploading: false, error: true } : p))
          );
        });
    }

    if (inputRef.current) inputRef.current.value = '';
  };

  const removeImage = (id: string) => {
    const img = images.find(i => i.id === id);
    if (img?.localUrl) URL.revokeObjectURL(img.localUrl);
    onChange(images.filter(i => i.id !== id));
  };

  return (
    <div className="flex gap-2 flex-wrap">
      {images.map(img => (
        <div key={img.id} className="relative w-20 h-20 rounded-lg overflow-hidden border border-border shrink-0">
          <img
            src={img.localUrl}
            alt="Preview"
            className={`w-full h-full object-cover ${img.uploading ? 'opacity-50' : ''} ${img.error ? 'border-2 border-red-500' : ''}`}
          />
          {img.uploading && (
            <div className="absolute inset-0 flex items-center justify-center bg-black/20">
              <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
            </div>
          )}
          {img.error && (
            <div className="absolute inset-0 flex items-center justify-center bg-red-500/20 text-red-500 text-xs">
              !
            </div>
          )}
          <button
            onClick={() => removeImage(img.id)}
            className="absolute top-0.5 right-0.5 w-5 h-5 bg-black/50 rounded-full flex items-center justify-center hover:bg-black/70"
          >
            <X className="w-3 h-3 text-white" />
          </button>
        </div>
      ))}
      <button
        onClick={() => inputRef.current?.click()}
        className="w-20 h-20 rounded-lg border-2 border-dashed border-border flex items-center justify-center hover:border-primary/50 transition-colors shrink-0"
      >
        <ImagePlus className="w-5 h-5 text-muted-foreground" />
      </button>
      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        multiple
        onChange={handleSelect}
        className="hidden"
      />
    </div>
  );
}

export type { ImageInfo };
